package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainter
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * What the dialogs' cues show in the field, from the tower, at the market, by the pond and by the stream (companion/SCENES.md,
 * "Effects"): the hare startled out of the grass, Sivko coming back to the cart, the scarecrow's hat and scarf, the pedlar
 * on the road, the hen on the cart (a tap on her there is the cart's), Muri at the jetty, the tadpoles, the lamb drinking,
 * the fire brigade's pump and its hose, the first cart over the bridge. Each state is rendered for review
 * (build/scene-snapshots/cues); a level that ends a dialog kept for the day ("fx_stays") looks as the place does by itself
 * or shows what it says.
 */
class CueStatesRenderTest {
    private val dir = File("build/scene-snapshots/cues").apply { mkdirs() }
    private val bw = 270; private val bh = 297

    /** Who stands at each art's spots: the scenes' own people. */
    private val cast = mapOf(
        "field" to listOf("farmer", "child3", "shepherd"), "watchtower" to listOf("shepherd", "child1", "farmer"),
        "market" to listOf("grandma", "innkeeper", "winemaker"), "pond" to listOf("beekeeper", "child1", "grandpa"),
        "stream" to listOf("farmer", "grandma", "shepherd"),
        "smithy" to listOf("smith", "child1", "winemaker"), "campfire" to listOf("grandma", "grandpa", "shepherd"),
        "workshop" to listOf("farmer", "child3", "grandma"),
        "forest" to listOf("shepherd", "beekeeper", "hunter"), "alps" to listOf("grandpa", "shepherd", "child1"),
        "hills" to listOf("winemaker", "grandpa", "child1"),
    )

    private fun frame(art: String, fx: Map<String, Float>, hour: Float = 10f, month: Int = 7, time: Double = 3.0, world: SceneWorld = SceneWorld.ALL) = SceneFrame(
        time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), fx = fx, world = world, wild = emptySet(),
        people = SceneArt.personSlots.getValue(art).mapIndexed { k, slot -> PersonInScene("p-$slot", cast.getValue(art)[k], slot) },
    )

    private class Shot(val canvas: PixelCanvas, val hits: List<SceneHit>, val painter: ScenePainter)

    private fun render(art: String, f: SceneFrame, k: Int = 1): Shot {
        val c = PixelCanvas(bw * k, bh * k)
        val p = ScenePainters.create(art)
        return Shot(c, p.render(c, f, Lens(k, 0, 0, bw, bh)), p)
    }

    private fun px(art: String, fx: Map<String, Float>, hour: Float = 10f, month: Int = 7, world: SceneWorld = SceneWorld.ALL) =
        render(art, frame(art, fx, hour, month, world = world)).canvas.pixels.toList()

    private fun write(c: PixelCanvas, name: String) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    @Test fun `render every cue state for review`() {
        val states = listOf(
            Triple("field", "hare", listOf(0.15f, 0.45f, 0.7f)), Triple("field", "horse", listOf(0f, 0.5f, 1f)),
            Triple("field", "hat", listOf(0f, 0.3f, 0.6f, 1f)), Triple("field", "scarf", listOf(1f)),
            Triple("watchtower", "pedlar", listOf(0f, 0.3f, 0.6f, 0.9f)), Triple("market", "hen", listOf(0.5f, 1f)),
            Triple("pond", "dog", listOf(0f, 0.5f, 1f)), Triple("pond", "tadpoles", listOf(1f)),
            Triple("stream", "lamb", listOf(0f, 0.6f, 1f)), Triple("stream", "hose", listOf(0f, 0.5f, 1f)),
            Triple("stream", "cart", listOf(0f, 0.4f, 0.7f)),
            Triple("smithy", "iron", listOf(1f, 0.6f, 0.3f, 0.05f)), Triple("field", "face", listOf(0f, 0.3f, 0.6f, 1f)),
            Triple("pond", "heron", listOf(0.2f, 0.42f, 0.6f, 1f)), Triple("pond", "bite", listOf(0f, 0.5f, 0.8f, 1f)),
            Triple("pond", "bucket", listOf(1f)), Triple("campfire", "flock", listOf(0.25f, 0.5f, 0.75f, 0.95f)),
            Triple("workshop", "toy", listOf(0f, 0.4f, 0.7f, 1f)), Triple("smithy", "cart", listOf(0f, 0.45f, 0.7f, 1f)),
        )
        for ((art, name, levels) in states) for (v in levels) {
            val hour = when (name) { "hare", "dog" -> 6.5f; "horse", "lamb" -> 19f; else -> 10f }
            write(render(art, frame(art, mapOf(name to v), hour, month = if (name == "tadpoles") 4 else 7), 2).canvas, "$art-$name-$v")
        }
        // the scarecrow as Tine leaves it: the learner's hat down on its nose, grandpa's red scarf
        write(render("field", frame("field", mapOf("hat" to 1f, "scarf" to 1f)), 2).canvas, "field-hat-scarf")
        // as many as the dialog counts: Luka's 24 sheep, Janez's 8 fish; the shoe on the anvil with the sparks flying
        write(render("campfire", frame("campfire", mapOf("flock" to 0.5f)).copy(count = 24), 2).canvas, "campfire-flock-24")
        write(render("pond", frame("pond", mapOf("bucket" to 1f)).copy(count = 8), 2).canvas, "pond-bucket-8")
        write(render("smithy", frame("smithy", mapOf("iron" to 0.85f, "sparks" to 1f)), 2).canvas, "smithy-iron-sparks")
        // the forest's felled trees, the lost sheep, the bear's tracks and the does at dusk; the alps' lost bell, the milk pail
        // at dawn and the cows counted; the hills' tractor going past
        val more = listOf(
            Triple("forest", "fell", listOf(0f, 0.15f, 0.5f, 0.8f, 0.9f, 1f)), Triple("forest", "fell-oak", listOf(0.5f, 0.9f, 1f)),
            Triple("forest", "sheep", listOf(0f, 0.1f, 0.5f, 1f)), Triple("forest", "tracks", listOf(0.5f, 1f)),
            Triple("forest", "does", listOf(0.5f, 1f)), Triple("alps", "bell-lost", listOf(0f, 0.5f, 1f)),
            Triple("alps", "pail", listOf(0.5f, 1f)), Triple("alps", "cows", listOf(1f)), Triple("hills", "tractor", listOf(0.5f, 0.9f)),
        )
        for ((art, name, levels) in more) for (v in levels) {
            val hour = when (name) { "does" -> 20.2f; "pail" -> 6.5f; else -> 10f }
            write(render(art, frame(art, mapOf(name to v), hour), 2).canvas, "$art-$name-$v")
        }
        write(render("forest", frame("forest", mapOf("sheep" to 0f, "sheepbells" to 1f)), 2).canvas, "forest-sheep-bell")
        write(render("forest", frame("forest", emptyMap(), world = SceneWorld(projects = mapOf("mlaj" to 0))), 2).canvas, "forest-mlaj-0")
        write(render("forest", frame("forest", emptyMap(), world = SceneWorld(projects = mapOf("mlaj" to 1))), 2).canvas, "forest-mlaj-1")
        write(render("forest", frame("forest", mapOf("does" to 1f), 20.2f).copy(count = 9), 2).canvas, "forest-does-9")
        write(render("alps", frame("alps", mapOf("cows" to 0.55f)).copy(count = 16), 2).canvas, "alps-cows-16-0.55")
        write(render("alps", frame("alps", mapOf("cows" to 1f)).copy(count = 12), 2).canvas, "alps-cows-12")
        write(render("alps", frame("alps", mapOf("bell-lost" to 0f, "cowbells" to 0.5f)), 2).canvas, "alps-bell-lost-ringing")
    }

    @Test fun `the forest's spruce is felled for the maypole, lies by its stump and is carried off`() {
        val standing = SceneWorld(projects = mapOf("mlaj" to 0))
        val felled = SceneWorld(projects = mapOf("mlaj" to 1))
        assertNotEquals("felled once the step is done", px("forest", emptyMap(), world = standing), px("forest", emptyMap(), world = felled))
        assertEquals("a cue at 0 stands it as before the step", px("forest", emptyMap(), world = standing), px("forest", mapOf("fell" to 0f), world = felled))
        assertEquals("a cue at 1 lays it as felled", px("forest", emptyMap(), world = felled), px("forest", mapOf("fell" to 1f), world = standing))
        assertNotEquals("carried off: only the stump", px("forest", emptyMap(), world = felled), px("forest", emptyMap()))
        assertNotEquals("the oak falls too", px("forest", emptyMap()), px("forest", mapOf("fell-oak" to 1f)))
    }

    @Test fun `the lost sheep is out of sight, comes home and is there at 1 as by herself`() {
        val sheep = SceneTarget.Thing("sheep")
        assertTrue("lost: not there to tap", render("forest", frame("forest", mapOf("sheep" to 0f))).hits.none { it.target == sheep })
        assertTrue("peeking out: there to tap", render("forest", frame("forest", mapOf("sheep" to 0.1f))).hits.any { it.target == sheep })
        assertNotEquals("on her way", px("forest", emptyMap()), px("forest", mapOf("sheep" to 0.5f)))
        assertEquals("home: as without the cue (kept for the day)", px("forest", emptyMap()), px("forest", mapOf("sheep" to 1f)))
    }

    @Test fun `the cow's lost bell lies in the grass and is back on her at 1, the tractor gone at 1`() {
        val bell = SceneTarget.Thing("cowbell")
        val lost = render("alps", frame("alps", mapOf("bell-lost" to 0f)))
        assertTrue("in the grass, there to tap", lost.hits.any { it.target == bell })
        assertNotEquals("not on her", px("alps", emptyMap()), lost.canvas.pixels.toList())
        assertEquals("back on her: as by itself (kept for the day)", px("alps", emptyMap()), px("alps", mapOf("bell-lost" to 1f)))
        assertNotEquals("the pail", px("alps", emptyMap()), px("alps", mapOf("pail" to 1f)))
        assertNotEquals("the tractor coming", px("hills", emptyMap()), px("hills", mapOf("tractor" to 0.5f)))
        assertEquals("the tractor past us and gone (kept for the day)", px("hills", emptyMap()), px("hills", mapOf("tractor" to 1f)))
    }

    @Test fun `the hare stirs the grass, leaps out and is gone at 1, the grass left`() {
        val stir = px("field", mapOf("hare" to 0.2f))
        assertNotEquals("the grass stirs where it hides", px("field", emptyMap()), stir)
        assertNotEquals("out and away", stir, px("field", mapOf("hare" to 0.5f)))
        assertEquals("at 1 gone: the clump of grass alone, still", px("field", mapOf("hare" to 0.0001f)), px("field", mapOf("hare" to 1f)))
    }

    @Test fun `Sivko is away, comes back to the cart and is home at 1 as by himself`() {
        val away = render("field", frame("field", mapOf("horse" to 0f)))
        assertTrue("away: not there to tap", away.hits.none { it.target == SceneTarget.Thing("horse") })
        assertNotEquals("the shafts down", px("field", emptyMap()), away.canvas.pixels.toList())
        val far = render("field", frame("field", mapOf("horse" to 0.4f)))
        assertTrue("coming into the picture: scenery", far.hits.none { it.target == SceneTarget.Thing("horse") })
        val coming = render("field", frame("field", mapOf("horse" to 0.7f)))
        assertTrue("well on his way: there to tap", coming.hits.any { it.target == SceneTarget.Thing("horse") })
        assertEquals("home: as without the cue (kept for the day)", px("field", emptyMap()), px("field", mapOf("horse" to 1f)))
    }

    @Test fun `the scarecrow loses its hat, gets the learner's, and the scarf`() {
        val own = px("field", emptyMap()); val none = px("field", mapOf("hat" to 0f))
        assertNotEquals("no hat", own, none)
        assertNotEquals("the felt hat on its head", none, px("field", mapOf("hat" to 0.5f)))
        assertNotEquals("slipped onto its nose", px("field", mapOf("hat" to 0.5f)), px("field", mapOf("hat" to 1f)))
        assertNotEquals("the scarf", px("field", mapOf("hat" to 1f)), px("field", mapOf("hat" to 1f, "scarf" to 1f)))
    }

    @Test fun `the pedlar comes down the road and is in at 1`() {
        val plain = px("watchtower", emptyMap())
        assertNotEquals("on the road", plain, px("watchtower", mapOf("pedlar" to 0.4f)))
        assertNotEquals("nearer", px("watchtower", mapOf("pedlar" to 0.4f)), px("watchtower", mapOf("pedlar" to 0.8f)))
        assertEquals("in at the gate: as without him", plain, px("watchtower", mapOf("pedlar" to 1f)))
    }

    @Test fun `the hen up on the cart is the cart to tap, her coop still the hen`() {
        val plain = render("market", frame("market", emptyMap()))
        val on = render("market", frame("market", mapOf("hen" to 1f)))
        assertEquals("back in her coop at 0", plain.canvas.pixels.toList(), px("market", mapOf("hen" to 0f)))
        val w = plain.canvas.width
        val owners = plain.canvas.pixels.indices.filter { plain.canvas.pixels[it] != on.canvas.pixels[it] }
            .mapNotNull { on.painter.targetAt(on.canvas, it % w, it / w, 0) }.toSet()
        assertTrue("a tap on her on the cart picks the cart ($owners)", SceneTarget.Thing("cart") in owners)
        assertTrue("the coop is there to tap", on.hits.any { it.target == SceneTarget.Thing("hen") })
        assertNotEquals("on her way over the lane", plain.canvas.pixels.toList(), px("market", mapOf("hen" to 0.5f)))
    }

    @Test fun `Muri sleeps, sits up and barks by the jetty, and the tadpoles swim but not under the ice`() {
        val plain = px("pond", emptyMap(), hour = 6.5f)
        val asleep = px("pond", mapOf("dog" to 0f), hour = 6.5f)
        val up = px("pond", mapOf("dog" to 0.5f), hour = 6.5f)
        assertNotEquals("asleep", plain, asleep)
        assertNotEquals("sitting up", asleep, up)
        assertNotEquals("barking", up, px("pond", mapOf("dog" to 1f), hour = 6.5f))
        assertNotEquals("tadpoles in spring", px("pond", emptyMap(), month = 4), px("pond", mapOf("tadpoles" to 1f), month = 4))
        assertEquals("none under the ice", px("pond", emptyMap(), month = 1), px("pond", mapOf("tadpoles" to 1f), month = 1))
    }

    @Test fun `the lamb, the pump and the cart by the stream`() {
        val plain = px("stream", emptyMap())
        assertNotEquals("the lamb turned from the water", plain, px("stream", mapOf("lamb" to 0f)))
        assertNotEquals("the lamb drinking", px("stream", mapOf("lamb" to 0f)), px("stream", mapOf("lamb" to 1f)))
        assertNotEquals("the pump standing", plain, px("stream", mapOf("hose" to 0f)))
        assertNotEquals("the jet", px("stream", mapOf("hose" to 0f)), px("stream", mapOf("hose" to 0.5f)))
        assertNotEquals("the hose loose", px("stream", mapOf("hose" to 0.5f)), px("stream", mapOf("hose" to 1f)))
        assertNotEquals("the cart on the bridge", plain, px("stream", mapOf("cart" to 0.4f)))
        assertEquals("gone at 1 (kept for the day)", plain, px("stream", mapOf("cart" to 1f)))
        val unfinished = SceneWorld(projects = mapOf("most" to 4))
        assertEquals("no cart before the deck is laid", px("stream", emptyMap(), world = unfinished), px("stream", mapOf("cart" to 0.4f), world = unfinished))
    }
}
