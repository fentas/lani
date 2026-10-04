package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
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
 * The scenes show what the village has built ([SceneWorld]): a fixture is drawn only when it's there (the kozolec in the
 * field, the tent at the campfire, the well and the church on the square), a project's as far as its steps have come
 * (France's mill and the cart bridge by the stream, the double hayrack, the maypole, the town clock), and everything by
 * default. Renders each art's stages side by side for review (build/scene-snapshots/world/00-before-after.png: a row
 * per art, nothing built on the left, everything on the right), at every detail.
 */
class SceneWorldRenderTest {
    private val dir = File("build/scene-snapshots/world").apply { mkdirs() }

    private fun frame(art: String, world: SceneWorld, hour: Float = 10f, month: Int = 7, people: Boolean = false) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), world = world,
        people = if (people) SceneArt.personSlots.getValue(art).mapIndexed { k, s -> PersonInScene("p-$s", SceneArt.people[k], s) } else emptyList(),
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    /** A village at the last age with [buildings] (all of them when null) and [projects] at their steps (the rest none). */
    private fun world(buildings: Set<BuildingType>? = BuildingType.entries.toSet(), projects: Map<String, Int> = emptyMap()) =
        SceneWorld(Age.MESTO, buildings?.associateWith { 1 }, projects)

    private val noTent = BuildingType.entries.toSet() - BuildingType.TENT
    private val noKozolec = BuildingType.entries.toSet() - BuildingType.KOZOLEC
    private val bare = BuildingType.entries.toSet() - setOf(BuildingType.WELL, BuildingType.CHURCH)

    /** Each art's worlds, from nothing built to everything, with a label each. */
    private val stages: Map<String, List<Pair<String, SceneWorld>>> = mapOf(
        "stream" to (0..6).map { s -> "mlin, most $s/6" to world(projects = mapOf("mlin" to s, "most" to s)) },
        "field" to listOf("no kozolec" to world(noKozolec), "kozolec" to world()) +
            (1..6).map { s -> "toplar $s/6" to world(projects = mapOf("toplar" to s)) },
        "square" to listOf("no well, church" to world(bare), "well, church" to world()) +
            (1..5).map { s -> "mlaj $s/5" to world(projects = mapOf("mlaj" to s)) } +
            listOf(2, 6, 7).map { s -> "ura $s/7" to world(projects = mapOf("mlaj" to 5, "mestna_ura" to s)) },
        "campfire" to listOf("no tent" to world(noTent), "tent" to world()),
        // the rooms by their building's level: the kitchen of a hut (bare, then the stove and the table, then the clock and
        // the holy corner), the smithy (the bellows, then the wheel and the second anvil), the school (the map and the
        // globe, then the clock)
        "kitchen" to (1..3).map { "hut level $it" to SceneWorld.room(it, BuildingType.HUT) },
        "smithy" to (1..3).map { "level $it" to SceneWorld.room(it, BuildingType.SMITHY) },
        "school" to (1..3).map { "level $it" to SceneWorld.room(it, BuildingType.SCHOOL) },
        // the rooms of the houses (game/scene/Homes) by their house's level
        "livingroom" to (1..3).map { "house level $it" to SceneWorld.room(it, BuildingType.HOUSE) },
        "workshop" to (1..3).map { "house level $it" to SceneWorld.room(it, BuildingType.HOUSE) },
        "cellar" to (1..3).map { "house level $it" to SceneWorld.room(it, BuildingType.HOUSE) },
        "attic" to (1..3).map { "house level $it" to SceneWorld.room(it, BuildingType.HOUSE) },
    )

    @Test fun `render the stages side by side`() {
        val cols = stages.values.maxOf { it.size }
        val s = 2; val label = 14
        val img = BufferedImage(240 * s * cols, (160 * s + label) * stages.size, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for ((row, e) in stages.entries.withIndex()) for ((col, st) in e.value.withIndex()) {
            val c = render(e.key, frame(e.key, st.second, hour = if (e.key == "campfire") 21.5f else 10f))
            write(c, File(dir, "${e.key}-$col.png"), 3)
            crop[e.key]?.let { r -> writeCrop(c, r, File(dir, "crop-${e.key}-$col.png")) }
            val oy = row * (160 * s + label)
            for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB(col * 240 * s + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
            g.color = Color.WHITE
            g.drawString("${e.key}: ${st.first}", col * 240 * s + 4, oy + 11)
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "00-before-after.png"))
        // closer up, at night and in winter: the new things at every detail
        for ((art, list) in stages) for ((n, st) in list.withIndex()) for (k in 2..Lens.MAX_DETAIL) {
            val c = render(art, frame(art, st.second, hour = 22.5f, month = 1), k)
            if (n == list.lastIndex || n == list.size / 2) write(c, File(dir, "$art-$n-d$k.png"), 6 / k)
        }
    }

    @Test fun `every art with fixtures looks different with nothing built, and each stage differs from the one before`() {
        for ((art, list) in stages) {
            val all = render(art, frame(art, SceneWorld.ALL)).pixels.toList()
            val first = render(art, frame(art, list.first().second)).pixels.toList()
            assertNotEquals("$art: nothing built looks like everything built", all, first)
            var prev = first
            for ((name, w) in list.drop(1)) {
                val now = render(art, frame(art, w)).pixels.toList()
                assertNotEquals("$art: $name looks like the stage before", prev, now)
                prev = now
            }
            assertEquals("$art: the last stage is everything built", all, prev)
        }
    }

    @Test fun `the arts without fixtures look the same whatever is built`() {
        for (art in SceneArt.arts - stages.keys) {
            val all = render(art, frame(art, SceneWorld.ALL)).pixels.toList()
            val none = render(art, frame(art, SceneWorld.NONE)).pixels.toList()
            assertEquals("$art changes with the village", all, none)
        }
    }

    @Test fun `a fixture that isn't there has no hit even when its slot is asked for, and everything else keeps its hit`() {
        // sky things answer only while they show: the stars at night, the square's sun by day, the moon while it is up
        val sky = setOf("campfire/stars", "square/sun") + (if (si.lanisce.lani.game.render.Env(10f, 7, false).moonShows) emptySet() else setOf("campfire/moon"))
        for (art in SceneArt.arts) for (k in 1..Lens.MAX_DETAIL) {
            val slots = SceneArt.objects.getValue(art)
            val f = frame(art, SceneWorld.NONE)
            val hits = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), f, Lens(k, 0, 0, 240, 160))
            // the wild animals that aren't out today have no hit either (see WildlifeRenderTest)
            val away = si.lanisce.lani.game.scene.Wildlife.slots(art).toSet() - f.wildOf(art)
            for (slot in slots) {
                val there = SceneFixtures.there(art, slot, SceneWorld.NONE)
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                if (!there || slot in away) assertEquals("$art/$slot isn't built (or out): no hit (detail $k)", 0, n)
                else if ("$art/$slot" !in sky) assertEquals("$art/$slot at detail $k", 1, n)
            }
        }
    }

    @Test fun `people still stand at every spot with nothing built`() {
        for (art in SceneArt.arts) {
            val hits = ScenePainters.create(art).render(PixelCanvas(240, 160), frame(art, SceneWorld.NONE, people = true))
            for (spot in SceneArt.personSlots.getValue(art)) assertTrue("$art: $spot", hits.any { it.target == SceneTarget.Person("p-$spot") })
        }
    }

    @Test fun `the default frame is everything built`() {
        assertEquals(SceneWorld.ALL, SceneFrame().world)
        for ((art, _) in stages) {
            val plain = render(art, SceneFrame(time = 3.0, hour = 10f, month = 7, objects = SceneArt.objects.getValue(art).toSet())).pixels.toList()
            assertEquals(art, render(art, frame(art, SceneWorld.ALL)).pixels.toList(), plain)
        }
        assertFalse(SceneFixtures.there("stream", "mill", SceneWorld.NONE))
        assertTrue(SceneFixtures.there("stream", "bridge", SceneWorld.NONE)) // the plank footbridge is always there
    }

    /** The part of each art's picture where its fixtures stand (x, y, w, h), drawn bigger for review. */
    private val crop = mapOf(
        "stream" to intArrayOf(90, 60, 100, 50), "field" to intArrayOf(125, 40, 65, 45), "square" to intArrayOf(60, 20, 120, 80), "campfire" to intArrayOf(120, 50, 100, 70),
        "kitchen" to intArrayOf(40, 10, 140, 110),
    )

    private fun writeCrop(c: PixelCanvas, r: IntArray, f: File, scale: Int = 6) {
        val img = BufferedImage(r[2] * scale, r[3] * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until r[3] * scale) for (x in 0 until r[2] * scale) img.setRGB(x, y, c.pixels[(r[1] + y / scale) * c.width + r[0] + x / scale])
        ImageIO.write(img, "png", f)
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
