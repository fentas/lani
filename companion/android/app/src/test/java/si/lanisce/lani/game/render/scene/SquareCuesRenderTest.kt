package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * What the village square's dialogs and project steps make happen in its picture (companion/SCENES.md, "Effects"): the cat
 * up the linden and down again, Muri away and back, the ball lost under the bench and a goal, a cart up the square, the
 * band, the dancing, the maypole raised and the fountain's water. Each at its moments, the ends that a dialog keeps looking
 * as the square does by itself, and snapshots for review (build/scene-snapshots/square-cues).
 */
class SquareCuesRenderTest {
    private val bw = 270; private val bh = 297
    private val dir = File("build/scene-snapshots/square-cues").apply { mkdirs() }

    private val janez = PersonInScene("janez", "grandpa", "bench")
    private val otroci = PersonInScene("otroci", "child1", "play1")
    private val kids = listOf(otroci, PersonInScene("nejc", "child2", "play2"), PersonInScene("zala", "child3", "play3"))

    private fun frame(fx: Map<String, Float> = emptyMap(), people: List<PersonInScene> = listOf(janez, otroci), world: SceneWorld = SceneWorld.ALL, time: Double = 3.0, hour: Float = 15f, month: Int = 7) =
        SceneFrame(time = time, hour = hour, month = month, objects = SceneArt.objects.getValue("square").toSet(), people = people, fx = fx, world = world)

    private fun render(f: SceneFrame, k: Int = 1): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(bw * k, bh * k)
        return c to ScenePainters.create("square").render(c, f, Lens(k, 0, 0, bw, bh))
    }

    private fun pixels(f: SceneFrame) = render(f).first.pixels.toList()

    private fun hit(hits: List<SceneHit>, slot: String) = hits.firstOrNull { it.target == SceneTarget.Thing(slot) }

    /** A village with the square and its projects at [projects] (the others none). */
    private fun world(projects: Map<String, Int>, down: Set<String> = emptySet()) = SceneWorld(Age.MESTO, null, projects, down = down)

    private fun write(c: PixelCanvas, name: String) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    @Test fun `the cat is up the linden at once, comes down it, and lies on the bench again as by herself`() {
        for (k in 1..Lens.MAX_DETAIL) {
            val (_, plain) = render(frame(), k)
            val bench = hit(plain, "bench")!!
            val (_, up) = render(frame(mapOf("cat" to 0f)), k)
            val cat = hit(up, "cat")
            assertNotNull("the cat can be tapped up there (detail $k)", cat)
            assertTrue("up the linden, high over the bench (detail $k): $cat over $bench", cat!!.bottom < bench.top - 25)
            val (_, half) = render(frame(mapOf("cat" to 0.5f)), k)
            val trunk = hit(half, "cat")!!
            assertTrue("halfway down, on the trunk between them (detail $k)", trunk.bottom > cat.bottom && trunk.bottom < bench.top + 2)
        }
        assertEquals("down again, she is as she is by herself", pixels(frame()), pixels(frame(mapOf("cat" to 1f))))
    }

    @Test fun `Muri is away at once, comes to the flowers, runs home, and wags`() {
        for (k in 1..Lens.MAX_DETAIL) {
            val (_, away) = render(frame(mapOf("dog" to 0f)), k)
            assertNull("away: nothing of him to tap (detail $k)", hit(away, "dog"))
            val (_, back) = render(frame(mapOf("dog" to 0.5f)), k)
            val dog = hit(back, "dog")!!; val flowers = hit(back, "flowers")!!
            assertTrue("sniffing at the flower bed's end (detail $k): $dog by $flowers", dog.left < flowers.right + 6 * k && dog.right > flowers.right - 4 * k && dog.top < flowers.bottom)
        }
        assertEquals("home again, he sits as ever", pixels(frame()), pixels(frame(mapOf("dog" to 1f))))
        assertNotEquals("wagging hard", pixels(frame()), pixels(frame(mapOf("wag" to 1f))))
    }

    @Test fun `the ball is lost under the bench, a tap there is on the bench, and back in play at 1`() {
        for (k in 1..Lens.MAX_DETAIL) {
            val (plain, plainHits) = render(frame(), k)
            val (lost, hits) = render(frame(mapOf("ball" to 0f)), k)
            assertNull("lost, the ball isn't to be tapped for its word (detail $k)", hit(hits, "ball"))
            val bench = hit(hits, "bench")!!
            assertEquals("the bench is where it was (detail $k)", hit(plainHits, "bench"), bench)
            // the ball's pixels under the seat, in the lower half of the bench, are the bench's: a tap on them is on the bench
            val lower = (bench.top + (bench.bottom - bench.top) / 2) * k
            val under = (lower until bench.bottom * k).sumOf { y -> (bench.left * k until bench.right * k).count { x -> lost.pixels[y * lost.width + x] != plain.pixels[y * plain.width + x] } }
            assertTrue("the ball shows under the bench (detail $k): $under px", under > 12 * k * k)
            val p = ScenePainters.create("square")
            val c = PixelCanvas(bw * k, bh * k); p.render(c, frame(mapOf("ball" to 0f)), Lens(k, 0, 0, bw, bh))
            val spot = (lower until bench.bottom * k).flatMap { y -> (bench.left * k until bench.right * k).map { x -> x to y } }
                .first { (x, y) -> lost.pixels[y * lost.width + x] != plain.pixels[y * plain.width + x] && c.ids[y * c.width + x] != 0 }
            assertEquals("tapped, the ball under it is the bench (detail $k)", SceneTarget.Thing("bench"), p.targetAt(c, spot.first, spot.second, 0))
        }
        assertEquals("back in play", pixels(frame()), pixels(frame(mapOf("ball" to 1f))))
    }

    @Test fun `a goal, the ball flies between the satchels and is kicked back into play`() {
        val (_, play) = render(frame(people = kids))
        val (_, goal) = render(frame(mapOf("goal" to 0.6f), people = kids))
        assertTrue("in the goal, behind the game", hit(goal, "ball")!!.bottom < hit(play, "ball")!!.top)
        assertEquals("kicked back, the game goes on", pixels(frame(people = kids)), pixels(frame(mapOf("goal" to 1f), people = kids)))
    }

    @Test fun `a cart comes up the square and stops, the pump on it, or the stone until it is unloaded`() {
        val none = world(emptyMap())
        val plain = pixels(frame(world = none))
        for (u in listOf(0.2f, 0.6f, 1f)) assertNotEquals("the pump's cart at $u", plain, pixels(frame(mapOf("cart-pump" to u), world = none)))
        assertNotEquals("the pump and the stone are different loads", pixels(frame(mapOf("cart-pump" to 1f), world = none)), pixels(frame(mapOf("cart-stone" to 1f), world = none)))
        // once the fountain's stone lies on the square, the cart stands there empty
        val laid = world(mapOf("vodnjak_na_trgu" to 2))
        val withStone = pixels(frame(mapOf("cart-stone" to 1f), world = laid))
        val (c, _) = render(frame(mapOf("cart-stone" to 1f), world = none))
        assertNotEquals(withStone, c.pixels.toList())
    }

    @Test fun `the maypole is raised only once its wreath is woven and while it is in season, and stands at 1`() {
        val ready = world(mapOf("mlaj" to 4))
        assertNotEquals("halfway up", pixels(frame(world = ready)), pixels(frame(mapOf("maypole" to 0.5f), world = ready)))
        assertEquals("up, it stands as when finished", pixels(frame(world = world(mapOf("mlaj" to 5)))), pixels(frame(mapOf("maypole" to 1f), world = ready)))
        val early = world(mapOf("mlaj" to 3))
        assertEquals("no wreath yet: nothing to raise", pixels(frame(world = early)), pixels(frame(mapOf("maypole" to 0.5f), world = early)))
        val down = world(mapOf("mlaj" to 5), down = setOf("mlaj"))
        assertEquals("out of its season there is no maypole to raise", pixels(frame(world = down)), pixels(frame(mapOf("maypole" to 0.5f), world = down)))
    }

    @Test fun `the fountain's water runs along the ditch while it is dug, and from the pipes once they are in`() {
        fun st(n: Int) = world(mapOf("vodnjak_na_trgu" to n))
        assertNotEquals("the ditch and its water, while its step plays", pixels(frame(world = st(3))), pixels(frame(mapOf("fountain" to 1f), world = st(3))))
        assertNotEquals("the water opened at the pipes", pixels(frame(world = st(5))), pixels(frame(mapOf("fountain" to 1f), world = st(5))))
        assertNotEquals("turned off, the finished fountain is dry", pixels(frame(world = st(6))), pixels(frame(mapOf("fountain" to 0f), world = st(6))))
        assertEquals("only a stone yet: no water", pixels(frame(world = st(2))), pixels(frame(mapOf("fountain" to 1f), world = st(2))))
    }

    @Test fun `render the cues at their moments for review`() {
        val shots = linkedMapOf<String, SceneFrame>(
            "cat-0-up" to frame(mapOf("cat" to 0f), time = 3.3),
            "cat-1-limb" to frame(mapOf("cat" to 0.2f)),
            "cat-2-trunk" to frame(mapOf("cat" to 0.5f)),
            "cat-3-hop" to frame(mapOf("cat" to 0.8f)),
            "cat-4-bench" to frame(mapOf("cat" to 1f)),
            "cat-0-winter" to frame(mapOf("cat" to 0f), month = 1),
            "muri-0-away" to frame(mapOf("dog" to 0f), hour = 19f),
            "muri-1-coming" to frame(mapOf("dog" to 0.3f), hour = 19f),
            "muri-2-flowers" to frame(mapOf("dog" to 0.5f), hour = 19f),
            "muri-3-running" to frame(mapOf("dog" to 0.75f), hour = 19f),
            "muri-4-home-wag" to frame(mapOf("dog" to 1f, "wag" to 1f), hour = 19f, time = 3.35),
            "ball-0-lost" to frame(mapOf("ball" to 0f)),
            "ball-1-rolling" to frame(mapOf("ball" to 0.5f)),
            "goal-0-shot" to frame(mapOf("goal" to 0.15f), people = kids),
            "goal-1-in" to frame(mapOf("goal" to 0.6f), people = kids, time = 3.2),
            "goal-2-back" to frame(mapOf("goal" to 0.93f), people = kids),
            "cart-pump-0-far" to frame(mapOf("cart-pump" to 0.3f), world = world(emptyMap())),
            "cart-pump-1-near" to frame(mapOf("cart-pump" to 0.8f), world = world(emptyMap())),
            "cart-pump-2-stopped" to frame(mapOf("cart-pump" to 1f), world = world(emptyMap())),
            "cart-stone-stopped" to frame(mapOf("cart-stone" to 1f), world = world(emptyMap()), hour = 12f),
            "cart-stone-unloaded" to frame(mapOf("cart-stone" to 1f), world = world(mapOf("vodnjak_na_trgu" to 2))),
            "band" to frame(mapOf("band" to 1f), world = world(mapOf("gasilski_dom" to 5)), people = emptyList()),
            "band-dance" to frame(mapOf("band" to 1f, "dance" to 1f), world = world(mapOf("gasilski_dom" to 5)), people = emptyList(), hour = 20.5f),
            "maypole-0-lying" to frame(world = world(mapOf("mlaj" to 4)), month = 5),
            "maypole-1-rising" to frame(mapOf("maypole" to 0.25f, "band" to 1f), world = world(mapOf("mlaj" to 4)), month = 5),
            "maypole-2-rising" to frame(mapOf("maypole" to 0.6f, "band" to 1f), world = world(mapOf("mlaj" to 4)), month = 5),
            "maypole-3-up" to frame(mapOf("maypole" to 1f, "band" to 1f), world = world(mapOf("mlaj" to 4)), month = 5),
            "fountain-ditch-water" to frame(mapOf("fountain" to 0.7f), world = world(mapOf("vodnjak_na_trgu" to 3))),
            "fountain-pipes-open" to frame(mapOf("fountain" to 1f), world = world(mapOf("vodnjak_na_trgu" to 5))),
        )
        for ((name, f) in shots) for (k in listOf(1, 3)) write(render(f, k).first, "$name-d$k")
    }
}
