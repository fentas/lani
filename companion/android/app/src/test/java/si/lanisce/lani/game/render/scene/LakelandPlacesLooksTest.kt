package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
 * The Lake District's look of its landscape's beck and tarn and of the places its projects of the third wave open
 * (companion/SCENES.md "Culture looks"; docs/plans/05-built/lakeland-projects-b.md): the beck under the fells, the tarn with
 * the fells in its water, the mountain rescue hut, the playground by the school, the viewing station above the lake, and on
 * the green and in the field the drinking fountain, the church clock and the bank barn at every step of their projects. Each
 * beside the default (Primorska's) picture, by day, in the evening, in winter and closer up
 * (build/scene-snapshots/looks/lakeland-b-<art>…png); and checked: each looks the Lake District's own, every thing is drawn
 * and tappable once at every detail, the people stand where they did, each project's step shows on its picture, the
 * default culture's pictures are as before, and the pictures are the ones reviewed.
 */
class LakelandPlacesLooksTest {
    private val dir = File("build/scene-snapshots/looks").apply { mkdirs() }

    /** An art, the building its scene is seen in (none for a spot of the land), and who stands at its spots in the previews. */
    private class Art(val art: String, val type: BuildingType?, val sprites: List<String>)

    private val arts = listOf(
        Art("stream", null, listOf("farmer", "grandma", "shepherd")),
        Art("pond", null, listOf("beekeeper", "child2", "grandpa")),
        Art("firestation", null, listOf("shepherd", "smith", "farmer")),
        Art("playground", null, listOf("child2", "child3", "teacher")),
        Art("lookout", null, listOf("child1", "innkeeper", "grandpa")),
        Art("square", BuildingType.WELL, listOf("grandpa", "teacher", "child1", "child2", "child3", "smith", "farmer", "aunt")),
        Art("field", BuildingType.FIELD, listOf("farmer", "shepherd", "child3")),
    )

    /** A project of the green or the field: its art and the building its scene is seen in. */
    private class Project(val id: String, val art: Art)

    private val projects = listOf(
        Project("vodnjak_na_trgu", arts.single { it.art == "square" }),
        Project("mestna_ura", arts.single { it.art == "square" }),
        Project("toplar", arts.single { it.art == "field" }),
    )

    private fun world(a: Art, culture: String, projects: Map<String, Int>? = null) =
        SceneWorld(culture = culture, here = a.type?.let { Building("here", it, 0, level = Catalog.MAX_LEVEL) }, projects = projects)

    private fun frame(
        a: Art, culture: String, hour: Float, people: Boolean = true, month: Int = 6, sky: Sky = Sky.CLEAR,
        projects: Map<String, Int>? = null, fx: Map<String, Float> = emptyMap(),
    ) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(a.art).toSet(), world = world(a, culture, projects), sky = sky,
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(a.art).mapIndexed { k, s -> PersonInScene("p-$s", a.sprites[k % a.sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
        fx = fx,
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

    @Test fun `render the Lake District's beck, tarn and project places beside the default ones`() {
        val only = System.getProperty("lakeland.only")?.split(',')?.toSet()
        for (a in arts) {
            if (only != null && a.art !in only) continue
            sheet("lakeland-b-${a.art}.png", listOf(
                "June morning" to { c: String -> frame(a, c, 10.5f) },
                "October evening" to { c: String -> frame(a, c, 19.5f, month = 10) },
                "April morning" to { c: String -> frame(a, c, 9f, month = 4) },
                "January day" to { c: String -> frame(a, c, 12f, month = 1) },
                "January night" to { c: String -> frame(a, c, 22.5f, month = 1) },
                "rain in May" to { c: String -> frame(a, c, 11f, month = 5, sky = Sky(rain = 0.6f, gloom = 0.6f, wind = 0.4f)) },
            ), a.art)
            for (k in 2..3) write(render(a.art, frame(a, "lakeland", 10.5f, people = false), k), File(dir, "lakeland-b-${a.art}-k$k.png"))
            write(render(a.art, frame(a, "lakeland", 18.5f, month = 9), 2, 270, 297), File(dir, "lakeland-b-${a.art}-phone.png"))
        }
        for (p in projects) {
            if (only != null && p.art.art !in only) continue
            val n = SceneWorld().stages(p.id)
            sheet("lakeland-b-${p.id}-steps.png", (0..n).map { st -> "${p.id} $st/$n" to { c: String -> frame(p.art, c, 10.5f, people = false, projects = mapOf(p.id to st)) } }, p.art.art)
            for (k in 2..3) write(render(p.art.art, frame(p.art, "lakeland", 10.5f, people = false), k), File(dir, "lakeland-b-${p.id}-k$k.png"))
        }
        // the effects a project's steps and scenes play: the slate on the cart, the fountain's water down its trench and from
        // the lion's mouth, the clock striking, the stretcher wheeled to and fro, the whistle blown
        val square = arts.single { it.art == "square" }
        sheet("lakeland-b-square-fx.png", listOf(
            "the cart with the slate" to { c: String -> frame(square, c, 10.5f, people = false, projects = mapOf("vodnjak_na_trgu" to 1), fx = mapOf("cart-stone" to 1f)) },
            "the trench's water" to { c: String -> frame(square, c, 10.5f, people = false, projects = mapOf("vodnjak_na_trgu" to 4), fx = mapOf("fountain" to 0.6f)) },
            "the fountain running" to { c: String -> frame(square, c, 10.5f, people = false, fx = mapOf("fountain" to 1f)) },
            "the clock at night" to { c: String -> frame(square, c, 22f, people = false) },
        ), "square")
        val hut = arts.single { it.art == "firestation" }
        sheet("lakeland-b-firestation-fx.png", listOf(
            "the stretcher wheeled" to { c: String -> frame(hut, c, 10.5f, fx = mapOf("pump" to 1f)) },
            "the whistle, the bell" to { c: String -> frame(hut, c, 10.5f, fx = mapOf("horn" to 1f, "bell" to 1f)) },
            "the doors shut, the lantern" to { c: String -> frame(hut, c, 21f, fx = mapOf("doors" to 0f, "lantern" to 1f)) },
        ), "firestation")
    }

    @Test fun `the Lake District's look draws each of these places its own way`() {
        for (a in arts) {
            assertTrue("Lakeland's look touches ${a.art}", CultureLooks.of("lakeland").touches(a.art))
            val p = render(a.art, frame(a, "primorska", 10.5f, people = false)).pixels
            val l = render(a.art, frame(a, "lakeland", 10.5f, people = false)).pixels
            val n = p.indices.count { p[it] != l[it] }
            assertTrue("${a.art} looks Slovene in the Lake District: $n pixels differ", n > 600)
        }
    }

    @Test fun `in the Lake District each place draws what it has, once, and the people stand where they did`() {
        val failed = ArrayList<String>()
        for (a in arts) for (k in 1..3) {
            val w = world(a, "lakeland")
            val hits = ScenePainters.create(a.art).render(PixelCanvas(240 * k, 160 * k), frame(a, "lakeland", 10.5f), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.objects.getValue(a.art)) {
                if (slot in WILD || SceneFixtures.of(a.art, slot, "lakeland") == SceneFixtures.A_LOOK_ONLY) continue
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                val want = if (SceneFixtures.there(a.art, slot, w)) 1 else 0
                if (n != want) failed += "${a.art}/$slot at detail $k: tappable $n times, not $want"
            }
            val home = ScenePainters.create(a.art).render(PixelCanvas(240 * k, 160 * k), frame(a, "primorska", 10.5f), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.personSlots.getValue(a.art)) {
                val x = home.singleOrNull { it.target == SceneTarget.Person("p-$slot") } ?: continue
                val y = hits.single { it.target == SceneTarget.Person("p-$slot") }
                if (listOf(x.anchorX, x.anchorY) != listOf(y.anchorX, y.anchorY)) failed += "${a.art}: someone at $slot doesn't stand where they did (detail $k)"
            }
        }
        assertEquals(emptyList<String>(), failed)
    }

    @Test fun `each step of the green's and the field's projects shows on its picture in the Lake District`() {
        for (p in projects) {
            val n = SceneWorld().stages(p.id)
            var before = render(p.art.art, frame(p.art, "lakeland", 10.5f, people = false, projects = mapOf(p.id to 0))).pixels
            // the clock's first, third, fourth and fifth steps are made away from the tower (its size worked out at school, the
            // gears in the smithy, the hands and the face in the workshop and at school): the tower shows them once lifted in;
            // its last, the clock going, moves only its hands, a few pixels at the scene's own size (StepScenesRenderTest's
            // "mestna_ura/6" says the same)
            val unseen = if (p.id == "mestna_ura") setOf(1, 3, 4, 5, 7) else emptySet()
            for (st in 1..n) {
                val now = render(p.art.art, frame(p.art, "lakeland", 10.5f, people = false, projects = mapOf(p.id to st))).pixels
                val d = now.indices.count { now[it] != before[it] }
                if (st !in unseen) assertTrue("${p.id}: step $st looks the same as the one before ($d pixels)", d >= 15)
                before = now
            }
            val all = render(p.art.art, frame(p.art, "lakeland", 10.5f, people = false)).pixels
            val last = render(p.art.art, frame(p.art, "lakeland", 10.5f, people = false, projects = mapOf(p.id to n))).pixels
            val others = render(p.art.art, frame(p.art, "lakeland", 10.5f, people = false, projects = SceneWorld().let { w -> Catalog.projectFrames.associate { it.id to w.stages(it.id) } })).pixels
            assertTrue("${p.id}: finished, it is the picture of everything built", others.contentEquals(all))
            assertNotEquals("${p.id}: finished, it is drawn", last.toList(), render(p.art.art, frame(p.art, "lakeland", 10.5f, people = false, projects = mapOf(p.id to 0))).pixels.toList())
        }
    }

    @Test fun `the default culture's pictures don't change`() {
        for (a in arts) {
            val p = render(a.art, frame(a, "primorska", 10.5f)).pixels.toList()
            val none = render(a.art, frame(a, "nowhere", 10.5f)).pixels.toList()
            assertEquals("${a.art}: the default culture draws as a culture without a look", none, p)
        }
    }

    /** A picture's digest: the first 12 hex digits of the SHA-256 of its pixels. */
    private fun digest(c: PixelCanvas): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val b = java.nio.ByteBuffer.allocate(c.pixels.size * 4)
        for (p in c.pixels) b.putInt(p)
        return md.digest(b.array()).joinToString("") { "%02x".format(it) }.take(12)
    }

    /**
     * The pictures as the review saw them (this test's contact sheets), pinned: each art by day without people, and each step
     * of the green's and the field's projects. A look changed on purpose: its new digests from this test's message, after a
     * look at its contact sheets.
     */
    @Test fun `the Lake District's beck, tarn and project places are the ones reviewed`() {
        val got = LinkedHashMap<String, String>()
        for (a in arts) got[a.art] = digest(render(a.art, frame(a, "lakeland", 10.5f, people = false)))
        for (p in projects) for (st in 0..SceneWorld().stages(p.id)) got["${p.id}/$st"] = digest(render(p.art.art, frame(p.art, "lakeland", 10.5f, people = false, projects = mapOf(p.id to st))))
        val changed = got.filter { (k, v) -> REVIEWED[k] != v }
        assertTrue(
            "the pictures changed (${changed.keys}); as they are now:\n" + got.entries.joinToString(",\n") { (k, v) -> "\"$k\" to \"$v\"" },
            changed.isEmpty() && got.keys == REVIEWED.keys,
        )
    }

    private fun write(c: PixelCanvas, f: File) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, c.width, c.height, c.pixels, 0, c.width)
        ImageIO.write(img, "png", f)
    }

    companion object {
        /** The digests of the pictures reviewed (the test of that name). */
        private val REVIEWED: Map<String, String> = mapOf(
            "stream" to "df1abd4a8388",
            "pond" to "da847f28e613",
            "firestation" to "4a676c2b601e",
            "playground" to "347cdb5b7440",
            "lookout" to "38939aeae74e",
            "square" to "74415913ad5d",
            "field" to "6fe7900e36ac",
            "vodnjak_na_trgu/0" to "3afef2b11191",
            "vodnjak_na_trgu/1" to "9da07e18f55f",
            "vodnjak_na_trgu/2" to "d19539d8b7a7",
            "vodnjak_na_trgu/3" to "8a1cdd375fc7",
            "vodnjak_na_trgu/4" to "cb38d9e117ba",
            "vodnjak_na_trgu/5" to "15bd350bddcb",
            "vodnjak_na_trgu/6" to "784208f27afe",
            "mestna_ura/0" to "3afef2b11191",
            "mestna_ura/1" to "3afef2b11191",
            "mestna_ura/2" to "3d6f0b8fc47d",
            "mestna_ura/3" to "3d6f0b8fc47d",
            "mestna_ura/4" to "3d6f0b8fc47d",
            "mestna_ura/5" to "3d6f0b8fc47d",
            "mestna_ura/6" to "cbaf3e334d07",
            "mestna_ura/7" to "c0eec305f482",
            "toplar/0" to "dcc050158e44",
            "toplar/1" to "3be50dd20d6a",
            "toplar/2" to "23641f8162d4",
            "toplar/3" to "94b899412b42",
            "toplar/4" to "7b0f1e9c67b2",
            "toplar/5" to "51f0c57fce29",
            "toplar/6" to "6fe7900e36ac",
        )

        /** The wild animals: out by the day and the hour, not every frame. */
        private val WILD = setOf("hare", "butterfly", "fox", "stag", "hedgehog", "boar", "badger", "dormouse", "bat", "deer")
    }
}
