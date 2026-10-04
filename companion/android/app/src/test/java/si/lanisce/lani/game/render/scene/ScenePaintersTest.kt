package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Portraits
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFixtures
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders every art at the four times of day in summer and winter, with people at every spot, to PNG
 * snapshots (build/scene-snapshots, 4× nearest-neighbour) and a contact sheet, and checks the contract:
 * hits for every slot and person, sane rectangles, determinism and frame time. Also the people in every
 * pose (a sheet, and each pose differs from standing idle) and the portraits (a sheet of every sprite in
 * every pose, determinism, sizes, frame time).
 */
class ScenePaintersTest {
    private val paintedArts = SceneArt.arts

    private val dir = File("build/scene-snapshots").apply { mkdirs() }

    /** People at every spot of [art]; sprites round-robin from [offset] so every sprite shows up somewhere. */
    private fun everyone(art: String, offset: Int = 0, talking: Boolean = false): List<PersonInScene> =
        SceneArt.personSlots.getValue(art).mapIndexed { k, slot ->
            val sprite = SceneArt.people[(offset + k) % SceneArt.people.size]
            PersonInScene("p-$slot", sprite, slot, talking = talking && k == 0, pose = if (!talking && k == 1) Pose.entries[(offset + k) % Pose.entries.size] else Pose.IDLE)
        }

    private fun allObjects(art: String) = SceneArt.objects.getValue(art).toSet()

    /** "art/slot" of [art]'s things that aren't there with everything built: a room's that go as it is upgraded (the kitchen's hearth). */
    private fun gone(art: String): Set<String> =
        SceneArt.objects.getValue(art).filterNot { SceneFixtures.there(art, it, SceneWorld.ALL) }.map { "$art/$it" }.toSet()

    private data class Shot(val name: String, val art: String, val frame: SceneFrame, val w: Int = 240, val h: Int = 160)

    private val hours = listOf("morning" to 8.5f, "afternoon" to 14f, "evening" to 19.6f, "night" to 23.2f)

    private val shots: List<Shot> = buildList {
        var k = 0
        for (art in paintedArts) for ((season, month) in listOf("summer" to 7, "winter" to 1)) for ((hn, hour) in hours) {
            val hl = if (hn == "afternoon") SceneArt.objects.getValue(art)[k % 5] else if (hn == "night") "p-" + SceneArt.personSlots.getValue(art).first() else null
            add(Shot("${art}-${season}-${hn}", art, SceneFrame(time = 3.0 + k, hour = hour, month = month, objects = allObjects(art), people = everyone(art, k, talking = hn == "evening"), highlight = hl)))
            k++
        }
        // autumn and spring, without people (the scenery alone)
        for (art in paintedArts) {
            add(Shot("${art}-autumn-afternoon-empty", art, SceneFrame(time = 5.0, hour = 15.5f, month = 10, objects = allObjects(art))))
            add(Shot("${art}-spring-morning-empty", art, SceneFrame(time = 5.0, hour = 9f, month = 4, objects = allObjects(art))))
        }
        // a tall phone view and a wide landscape view
        for (art in paintedArts) {
            add(Shot("${art}-tall-270x297", art, SceneFrame(time = 4.0, hour = 18.5f, month = 9, objects = allObjects(art), people = everyone(art, 3)), 270, 297))
            add(Shot("${art}-wide-360x160", art, SceneFrame(time = 4.0, hour = 11f, month = 6, objects = allObjects(art), people = everyone(art, 5)), 360, 160))
            add(Shot("${art}-max-360x320", art, SceneFrame(time = 4.0, hour = 22.5f, month = 12, objects = allObjects(art), people = everyone(art, 8)), 360, 320))
        }
    }

    @Test fun `render all snapshots`() {
        for (s in shots) {
            val c = PixelCanvas(s.w, s.h)
            ScenePainters.of(s.art).render(c, s.frame)
            write(c, File(dir, "${s.name}.png"))
        }
    }

    @Test fun `render the contact sheet`() {
        val cols = hours.size; val arts = paintedArts.toList()
        val rows = arts.size * 2
        val scale = 2
        val img = BufferedImage(240 * cols * scale, 160 * rows * scale, BufferedImage.TYPE_INT_RGB)
        for ((ai, art) in arts.withIndex()) for ((si, month) in listOf(7, 1).withIndex()) for ((hi, hh) in hours.withIndex()) {
            val c = PixelCanvas(240, 160)
            ScenePainters.of(art).render(c, SceneFrame(time = 3.0 + hi, hour = hh.second, month = month, objects = allObjects(art), people = everyone(art, ai * 3 + hi, talking = hi == 2)))
            val row = ai * 2 + si
            for (y in 0 until 160 * scale) for (x in 0 until 240 * scale) img.setRGB(hi * 240 * scale + x, row * 160 * scale + y, c.pixels[(y / scale) * 240 + x / scale])
        }
        ImageIO.write(img, "png", File(dir, "00-contact-sheet.png"))
    }

    /** Every sprite standing, talking, walking and sitting, on plain grass, for review. */
    private class PeopleSheet : ArtPainter() {
        override val art = "people-sheet"
        override fun backdrop(frame: SceneFrame): Int = 0xFF5F983D.toInt()
        override fun paint() {
            c.penId = 0
            for (y in 0 until h) for (x in 0 until w) c.set(x, y, if ((x / 24 + y / 44) % 2 == 0) 0xFF72AC47.toInt() else 0xFF5F983D.toInt())
            for ((k, sprite) in SceneArt.people.withIndex()) {
                val bx = 12 + k * 24
                person(PersonInScene("s$k", sprite, "a"), bx, 42, flip = false)
                person(PersonInScene("t$k", sprite, "b", talking = true), bx, 86, flip = true)
                person(PersonInScene("w$k", sprite, "c"), bx, 130, flip = false, walking = true)
                person(PersonInScene("z$k", sprite, "d"), bx, 172, flip = false, seated = true)
            }
        }
    }

    @Test fun `render the people sheet`() {
        val c = PixelCanvas(24 * SceneArt.people.size, 180)
        val hits = PeopleSheet().render(c, SceneFrame(time = 0.0, hour = 12f, month = 6))
        assertEquals(SceneArt.people.size * 4, hits.size)
        write(c, File(dir, "00-people-sheet.png"), scale = 6)
    }

    /** Every sprite in every pose (rows), facing right; the last rows are the village's own people with a few seeds. */
    private class PosesSheet(val time: Double) : ArtPainter() {
        override val art = "poses-sheet"
        override fun backdrop(frame: SceneFrame): Int = 0xFF5F983D.toInt()
        override fun paint() {
            c.penId = 0
            for (y in 0 until h) for (x in 0 until w) c.set(x, y, if ((x / 26 + y / 48) % 2 == 0) 0xFF72AC47.toInt() else 0xFF5F983D.toInt())
            for ((k, sprite) in SceneArt.people.withIndex()) for ((p, pose) in Pose.entries.withIndex()) {
                person(PersonInScene("$sprite-$p", sprite, "a", pose = pose), 13 + k * 26, 44 + p * 48, flip = false)
            }
            val extra = Pose.entries.size
            for (k in 0 until SceneArt.people.size) {
                val sprite = if (k % 2 == 0) "woman" else "man"
                person(PersonInScene("villager-$k", sprite, "a"), 13 + k * 26, 44 + extra * 48, flip = k % 3 == 0)
            }
        }
    }

    @Test fun `render the poses sheet`() {
        val c = PixelCanvas(26 * SceneArt.people.size, 48 * (Pose.entries.size + 1))
        val hits = PosesSheet(0.4).render(c, SceneFrame(time = 0.4, hour = 12f, month = 6))
        assertEquals(SceneArt.people.size * (Pose.entries.size + 1), hits.size)
        for (hit in hits) {
            val hh = hit.bottom - hit.top; val ww = hit.right - hit.left
            // asleep, they lie down on a pillow: wider than tall
            val lying = SceneArt.people.any { hit.target == SceneTarget.Person("$it-${Pose.SLEEP.ordinal}") }
            if (lying) assertTrue("${hit.target} lies person-sized: $hit", hh in 10..30 && ww in 14..40)
            else assertTrue("${hit.target} is person-sized: $hit", hh in 20..46 && ww in 8..30)
        }
        write(c, File(dir, "00-poses-sheet.png"), scale = 5)
    }

    /** One sprite alone on a small canvas, for comparing poses. */
    private class OnePerson(val p: PersonInScene) : ArtPainter() {
        override val art = "one"
        override fun backdrop(frame: SceneFrame): Int = 0xFF5F983D.toInt()
        override fun paint() { person(p, 24, 52, flip = false) }
    }

    @Test fun `every pose renders for every sprite and differs from idle`() {
        for (sprite in SceneArt.people) {
            val idle = (0..2).map { k -> PixelCanvas(48, 56).also { OnePerson(PersonInScene("x", sprite, "a")).render(it, SceneFrame(time = k * 0.4)) }.pixels.toList() }
            for (pose in Pose.entries) {
                if (pose == Pose.IDLE) continue
                val posed = (0..2).map { k -> PixelCanvas(48, 56).also { OnePerson(PersonInScene("x", sprite, "a", pose = pose)).render(it, SceneFrame(time = k * 0.4)) }.pixels.toList() }
                assertNotEquals("$sprite $pose looks like idle", idle, posed)
            }
            // talking alone is the talk pose
            val talk = (0..2).map { k -> PixelCanvas(48, 56).also { OnePerson(PersonInScene("x", sprite, "a", pose = Pose.TALK)).render(it, SceneFrame(time = k * 0.4)) }.pixels.toList() }
            val talking = (0..2).map { k -> PixelCanvas(48, 56).also { OnePerson(PersonInScene("x", sprite, "a", talking = true)).render(it, SceneFrame(time = k * 0.4)) }.pixels.toList() }
            assertEquals("$sprite talking", talk, talking)
        }
    }

    @Test fun `the village's own people vary with their id`() {
        for (sprite in listOf("woman", "man")) {
            val renders = (0 until 8).map { k -> PixelCanvas(48, 56).also { OnePerson(PersonInScene("villager-$k", sprite, "a")).render(it, SceneFrame(time = 0.0)) }.pixels.toList() }.toSet()
            assertTrue("$sprite: ${renders.size} looks from 8 ids", renders.size >= 4)
        }
    }

    @Test fun `every art has a real painter`() {
        for (art in paintedArts) assertTrue(art, ScenePainters.of(art) !is ScenePainters.Placeholder)
    }

    @Test fun `every slot returns exactly one hit when requested and none when not`() {
        for (art in paintedArts) for (hour in floatArrayOf(10f, 23f)) {
            val c = PixelCanvas(240, 160)
            // every wild animal out at once (the day's mix has a few of them, see WildlifeRenderTest)
            val wild = si.lanisce.lani.game.scene.Wildlife.all(art)
            val hits = ScenePainters.of(art).render(c, SceneFrame(time = 2.0, hour = hour, month = 6, objects = allObjects(art), wild = wild))
            // Sky things answer only while they show: the stars at night, the square's sun by day, the moon while it is up;
            // a room's things that go as it is upgraded (the kitchen's hearth and straw bed) aren't there when all is built.
            val hidden = (if (hour < 20f) setOf("campfire/stars") else setOf("square/sun")) +
                (if (Env(hour, 6, false).moonShows) emptySet() else setOf("campfire/moon")) + gone(art)
            for (slot in SceneArt.objects.getValue(art)) {
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                assertEquals("$art/$slot at $hour h", if ("$art/$slot" in hidden) 0 else 1, n)
            }
            assertEquals("$art: only things", hits.size, SceneArt.objects.getValue(art).count { "$art/$it" !in hidden })
            val none = ScenePainters.of(art).render(c, SceneFrame(time = 2.0, hour = hour, month = 6))
            assertTrue("$art: nothing requested, nothing hit", none.isEmpty())
            val one = ScenePainters.of(art).render(c, SceneFrame(time = 2.0, hour = hour, month = 6, objects = setOf(SceneArt.objects.getValue(art).last()), wild = wild))
            assertEquals("$art: one requested", 1, one.size)
        }
    }

    @Test fun `every person spot places the person with a hit`() {
        for (art in paintedArts) for (sprite in SceneArt.people) {
            val spots = SceneArt.personSlots.getValue(art)
            val people = spots.map { PersonInScene("id-$it", sprite, it, talking = true) }
            val c = PixelCanvas(240, 160)
            val hits = ScenePainters.of(art).render(c, SceneFrame(time = 1.0, hour = 12f, month = 6, people = people))
            for (spot in spots) {
                val hs = hits.filter { it.target == SceneTarget.Person("id-$spot") }
                assertEquals("$art/$spot/$sprite", 1, hs.size)
                val hit = hs.first()
                val hh = hit.bottom - hit.top; val ww = hit.right - hit.left
                assertTrue("$art/$spot/$sprite is person-sized: $hit", hh in 20..46 && ww in 8..30)
            }
        }
    }

    @Test fun `hits lie inside the canvas and have sane sizes`() {
        for (s in shots) {
            val c = PixelCanvas(s.w, s.h)
            val hits = ScenePainters.of(s.art).render(c, s.frame)
            val seen = HashSet<SceneTarget>()
            for (hit in hits) {
                assertTrue("${s.name} $hit inside", hit.left >= 0 && hit.top >= 0 && hit.right <= s.w && hit.bottom <= s.h)
                assertTrue("${s.name} $hit non-empty", hit.right - hit.left >= 3 && hit.bottom - hit.top >= 3)
                assertTrue("${s.name} $hit not the whole canvas", (hit.right - hit.left) * (hit.bottom - hit.top) < s.w * s.h * 0.6)
                assertTrue("${s.name} $hit anchor", hit.anchorX in hit.left..hit.right && hit.anchorY == hit.top)
                assertTrue("${s.name} $hit once", seen.add(hit.target))
            }
            // Sky things answer only while they show (see the painters): the stars at night, the sun by day, the moon while it is up.
            val env = Env(s.frame.hour, s.frame.month, false, 0f, s.frame.moon)
            val hidden = buildSet {
                if (s.art == "campfire" && env.dark <= 0.3f) add("stars")
                if (s.art == "campfire" && !env.moonShows) add("moon")
                if (s.art == "square" && env.sun <= -0.12f) add("sun")
                // the wild animals that aren't out (the day's mix of the frame's village and day)
                addAll(si.lanisce.lani.game.scene.Wildlife.slots(s.art) - s.frame.wildOf(s.art))
                // a room's things that go as it is upgraded
                addAll(gone(s.art).map { it.substringAfter('/') })
            }
            val expect = s.frame.objects.count { it !in hidden } + s.frame.people.size
            assertEquals("${s.name}: a hit for every requested object and person", expect, hits.size)
        }
    }

    @Test fun `taps land on the thing drawn under them, to the pixel`() {
        // smoke, sparks and the stars are drawn without pixels of their own: the rectangles answer for them
        val rectOnly = setOf("campfire/smoke", "campfire/sparks", "campfire/stars")
        for (art in paintedArts) {
            val p = ScenePainters.of(art)
            val c = PixelCanvas(240, 160)
            val hits = p.render(c, SceneFrame(time = 2.0, hour = 12f, month = 6, objects = allObjects(art), people = everyone(art)))
            for (hit in hits) {
                val tg = hit.target
                if (tg is SceneTarget.Thing && "$art/${tg.slot}" in rectOnly) continue
                var found = false
                for (y in hit.top until hit.bottom) for (x in hit.left until hit.right) if (!found && p.targetAt(c, x, y, 0) == tg) found = true
                assertTrue("$art: $tg can be tapped on its own pixels", found)
            }
            // nothing tappable far out in the corner of a diorama or the sky; another canvas is not this frame's
            assertEquals("$art: not the last frame's canvas", null, p.targetAt(PixelCanvas(240, 160), 120, 80, 3))
        }
    }

    @Test fun `hits come front-most last`() {
        // the campfire: the stone ring's front half is painted after the fire, so the stones come after the fire
        val c = PixelCanvas(240, 160)
        val hits = ScenePainters.of("campfire").render(c, SceneFrame(time = 1.0, hour = 12f, month = 6, objects = allObjects("campfire"), people = everyone("campfire")))
        val order = hits.map { it.target }
        assertTrue(order.indexOf(SceneTarget.Thing("fire")) < order.indexOf(SceneTarget.Thing("stones")))
        assertTrue(order.indexOf(SceneTarget.Thing("tent")) < order.indexOf(SceneTarget.Person("p-left")))
        // the kitchen: the plate lies on the table
        val k = ScenePainters.of("kitchen").render(c, SceneFrame(time = 1.0, hour = 12f, month = 6, objects = allObjects("kitchen")))
        val table = k.first { it.target == SceneTarget.Thing("table") }; val plate = k.first { it.target == SceneTarget.Thing("plate") }
        assertTrue(k.indexOf(table) < k.indexOf(plate))
        assertTrue("plate on the table", plate.left >= table.left && plate.right <= table.right && plate.top >= table.top - 2 && plate.bottom <= table.bottom)
        // the school: the notebook lies on the desk; the smithy: the hammer on the anvil
        val sc = ScenePainters.of("school").render(c, SceneFrame(time = 1.0, hour = 12f, month = 6, objects = allObjects("school")))
        val desk = sc.first { it.target == SceneTarget.Thing("desk") }; val nb = sc.first { it.target == SceneTarget.Thing("notebook") }
        assertTrue(sc.indexOf(desk) < sc.indexOf(nb))
        assertTrue("notebook on the desk", nb.left >= desk.left && nb.right <= desk.right && nb.bottom <= desk.bottom)
        val sm = ScenePainters.of("smithy").render(c, SceneFrame(time = 1.0, hour = 12f, month = 6, objects = allObjects("smithy")))
        val anvil = sm.first { it.target == SceneTarget.Thing("anvil") }; val hammer = sm.first { it.target == SceneTarget.Thing("hammer") }
        assertTrue(sm.indexOf(anvil) < sm.indexOf(hammer))
    }

    @Test fun `rendering is deterministic and animated`() {
        for (art in paintedArts) {
            val f = SceneFrame(time = 3.0, hour = 21f, month = 9, objects = allObjects(art), people = everyone(art, 1, talking = true))
            val a = PixelCanvas(240, 160); val b = PixelCanvas(240, 160)
            ScenePainters.of(art).render(a, f)
            ScenePainters.of(art).render(b, f)
            assertArrayEquals(art, a.pixels, b.pixels)
            val c = PixelCanvas(240, 160)
            ScenePainters.of(art).render(c, f.copy(time = f.time + 0.5))
            assertNotEquals(art, a.pixels.toList(), c.pixels.toList())
        }
    }

    @Test fun `a frame renders fast`() {
        for (art in paintedArts) {
            val p = ScenePainters.of(art)
            val f = SceneFrame(time = 1.0, hour = 22.5f, month = 10, objects = allObjects(art), people = everyone(art, 2, talking = true), highlight = SceneArt.objects.getValue(art).first())
            val c = PixelCanvas(360, 240)
            repeat(15) { p.render(c, f.copy(time = it / 12.0)) }
            val n = 40
            val t0 = System.nanoTime()
            repeat(n) { p.render(c, f.copy(time = 2 + it / 12.0)) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("ScenePainter %s: %.2f ms/frame (night, 360x240)".format(art, ms))
            assertTrue("$art too slow: $ms ms", ms < 20.0)
        }
    }

    // ------------------------------------------------------------------ portraits

    @Test fun `the portrait painter is the art's`() {
        assertTrue(Portraits.painter is PortraitsPainter)
    }

    @Test fun `render the portraits sheet`() {
        val px = 64; val scale = 2
        val sprites = SceneArt.people; val poses = Pose.entries
        val img = BufferedImage(px * poses.size * scale, px * sprites.size * scale, BufferedImage.TYPE_INT_RGB)
        for ((si, sprite) in sprites.withIndex()) for ((pi, pose) in poses.withIndex()) {
            val c = PixelCanvas(px, px)
            Portraits.painter.render(c, sprite, pose, 0.4 + pi * 0.3, if (pi < 6) 11f else 21f)
            for (y in 0 until px * scale) for (x in 0 until px * scale) img.setRGB(pi * px * scale + x, si * px * scale + y, c.pixels[(y / scale) * px + x / scale])
        }
        ImageIO.write(img, "png", File(dir, "00-portraits-sheet.png"))
        // a few of them big, to check the faces
        val close = listOf("grandma", "child2", "smith", "teacher", "baby")
        val big = BufferedImage(px * poses.size * 4, px * close.size * 4, BufferedImage.TYPE_INT_RGB)
        for ((si, sprite) in close.withIndex()) for ((pi, pose) in poses.withIndex()) {
            val c = PixelCanvas(px, px)
            Portraits.painter.render(c, sprite, pose, 0.4 + pi * 0.3, 11f)
            for (y in 0 until px * 4) for (x in 0 until px * 4) big.setRGB(pi * px * 4 + x, si * px * 4 + y, c.pixels[(y / 4) * px + x / 4])
        }
        ImageIO.write(big, "png", File(dir, "00-portraits-closeup.png"))
        // the hours, the sizes and a few seeds of the village's own people, on one more sheet
        val hoursSheet = BufferedImage(96 * 8 * scale, 96 * 3 * scale, BufferedImage.TYPE_INT_RGB)
        val painter = PortraitsPainter()
        for ((hi, hour) in listOf(6.5f, 9f, 13f, 17.5f, 19.5f, 21f, 23.5f, 2f).withIndex()) {
            for ((row, size) in listOf(48, 64, 96).withIndex()) {
                val c = PixelCanvas(size, size)
                painter.render(c, listOf("grandma", "child2", "man")[row], Pose.IDLE, 1.0 + hi, hour, hi * 7919)
                for (y in 0 until size * scale) for (x in 0 until size * scale) hoursSheet.setRGB(hi * 96 * scale + x, row * 96 * scale + y, c.pixels[(y / scale) * size + x / scale])
            }
        }
        ImageIO.write(hoursSheet, "png", File(dir, "00-portraits-hours-sizes.png"))
    }

    @Test fun `portraits render for every sprite and pose, deterministically and alive`() {
        for (sprite in SceneArt.people) for (pose in Pose.entries) {
            val a = PixelCanvas(64, 64); val b = PixelCanvas(64, 64)
            Portraits.painter.render(a, sprite, pose, 1.3, 15f); Portraits.painter.render(b, sprite, pose, 1.3, 15f)
            assertArrayEquals("$sprite $pose", a.pixels, b.pixels)
            assertTrue("$sprite $pose draws something", a.pixels.toSet().size > 12)
            // something moves (breath, blink, a mouth, a hand) over a couple of seconds
            val frames = (0 until 12).map { k -> PixelCanvas(64, 64).also { Portraits.painter.render(it, sprite, pose, 0.7 + k * 0.25, 15f) }.pixels.toList() }.toSet()
            assertTrue("$sprite $pose is alive", frames.size > 1)
            // the mood shows: the pose differs from idle
            if (pose != Pose.IDLE) {
                val idle = PixelCanvas(64, 64).also { Portraits.painter.render(it, sprite, Pose.IDLE, 1.3, 15f) }
                assertNotEquals("$sprite $pose differs from idle", idle.pixels.toList(), a.pixels.toList())
            }
        }
        // night and day differ, and any square-ish size works
        for (size in listOf(48, 64, 96, 80)) for (sprite in listOf("smith", "baby", "woman")) {
            val day = PixelCanvas(size, size).also { Portraits.painter.render(it, sprite, Pose.HAPPY, 2.0, 12f) }
            val night = PixelCanvas(size, size).also { Portraits.painter.render(it, sprite, Pose.HAPPY, 2.0, 23f) }
            assertNotEquals("$sprite at $size: night differs", day.pixels.toList(), night.pixels.toList())
        }
        val wide = PixelCanvas(96, 64); Portraits.painter.render(wide, "teacher", Pose.WAVE, 1.0, 12f)
        val tall = PixelCanvas(64, 96); Portraits.painter.render(tall, "teacher", Pose.WAVE, 1.0, 12f)
    }

    @Test fun `a portrait renders fast`() {
        val c = PixelCanvas(64, 64)
        repeat(30) { Portraits.painter.render(c, "grandma", Pose.TALK, it * 0.1, 12f) }
        var worst = 0.0
        for (sprite in listOf("grandma", "smith", "baby", "beekeeper")) for (pose in listOf(Pose.IDLE, Pose.CHEER, Pose.TALK)) {
            val n = 60
            val t0 = System.nanoTime()
            repeat(n) { Portraits.painter.render(c, sprite, pose, 2 + it * 0.1, 21f) }
            val us = (System.nanoTime() - t0) / 1e3 / n
            worst = maxOf(worst, us)
            println("Portrait %s %s: %.0f us/frame (64x64)".format(sprite, pose, us))
        }
        assertTrue("portraits too slow: $worst us", worst < 3000.0)
    }

    private fun write(c: PixelCanvas, f: File, scale: Int = 4) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }

    @Suppress("unused")
    private fun List<SceneHit>.named() = joinToString { "${it.target}@${it.left},${it.top}-${it.right},${it.bottom}" }
}
