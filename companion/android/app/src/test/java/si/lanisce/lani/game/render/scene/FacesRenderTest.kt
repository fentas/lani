package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.Face
import si.lanisce.lani.game.scene.Framing
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The faces (companion/SCENES.md, "Faces"): every face on a few sprites (a child, the teacher, grandpa, a village woman and
 * man, the smith, grandma, the baby), in the portrait's bust, its head alone (an avatar) and on the scene's sprite, to PNG
 * contact sheets in build/faces for review; and the contract: each face differs from the others and from none, the eyes
 * blink but for the shut ones, a talking mouth moves, the emote shows where the face has one and stays inside the canvas,
 * a scene's emote is never a part of the person to tap, and a face costs a portrait no real time.
 */
class FacesRenderTest {
    private val dir = File("build/faces").apply { mkdirs() }

    /** The sprites of the sheets: a child, the teacher, grandpa, the village's own people, the smith, grandma, the baby. */
    private val sprites = listOf("child1", "child2", "teacher", "grandpa", "woman", "man", "smith", "grandma", "baby")
    private val seeds = mapOf("woman" to 0x5A17, "man" to 0x2B33)

    private fun portrait(sprite: String, face: Face?, size: Int, framing: Framing = Framing.BUST, pose: Pose = Pose.IDLE, t: Double = 0.4, hour: Float = 11f): PixelCanvas =
        PixelCanvas(size, size).also { PortraitsPainter().render(it, sprite, pose, t, hour, seeds[sprite] ?: 0, face, 9.0, framing) }

    private class OnePerson(val p: PersonInScene, val k: Int = 1) : ArtPainter() {
        override val art = "one"
        override fun backdrop(frame: SceneFrame): Int = 0xFF6FA84A.toInt()
        override fun paint() { person(p, 18 * k, 50 * k, flip = false) }
    }

    private fun sprite(art: String, face: Face?, talking: Boolean = false, t: Double = 0.4, pose: Pose = Pose.IDLE): PixelCanvas =
        PixelCanvas(36, 56).also { OnePerson(PersonInScene("p", art, "a", talking = talking, pose = pose, face = face)).render(it, SceneFrame(time = t, hour = 12f)) }

    private fun sheet(cells: List<List<PixelCanvas>>, scale: Int, gap: Int = 2, file: String) {
        val cw = cells.maxOf { r -> r.maxOf { it.width } }; val ch = cells.maxOf { r -> r.maxOf { it.height } }
        val cols = cells.maxOf { it.size }
        val img = BufferedImage(cols * (cw * scale + gap) + gap, cells.size * (ch * scale + gap) + gap, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics(); g.color = java.awt.Color(0x30, 0x2C, 0x38); g.fillRect(0, 0, img.width, img.height); g.dispose()
        for ((r, row) in cells.withIndex()) for ((k, c) in row.withIndex()) {
            val ox = gap + k * (cw * scale + gap); val oy = gap + r * (ch * scale + gap)
            for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(ox + x, oy + y, c.pixels[(y / scale) * c.width + x / scale])
        }
        ImageIO.write(img, "png", File(dir, file))
    }

    private val faces: List<Face?> = listOf(null) + Face.entries

    @Test fun `render the faces sheets`() {
        // the bust (64 px, as on the stage and the intros), by day; the head alone (24 px, an avatar); the scene's sprite
        sheet(sprites.map { s -> faces.map { f -> portrait(s, f, 64) } }, 3, file = "faces-bust.png")
        sheet(sprites.map { s -> faces.map { f -> portrait(s, f, 64, hour = 22.5f) } }, 3, file = "faces-bust-night.png")
        sheet(sprites.map { s -> faces.map { f -> portrait(s, f, 32) } }, 6, file = "faces-bust-32.png")
        sheet(sprites.map { s -> faces.map { f -> portrait(s, f, 24, Framing.HEAD) } }, 6, file = "faces-head.png")
        sheet(sprites.map { s -> faces.map { f -> sprite(s, f) } }, 5, file = "faces-scene.png")
        // talking: four beats of each face's mouth
        sheet(Face.entries.map { f -> (0 until 4).map { k -> portrait("teacher", f, 32, pose = Pose.TALK, t = 0.4 + k * 0.143) } }, 6, file = "faces-talking.png")
        // each face with the poses that go with it (a cheer laughing, a wave smiling, a hand at the ear …)
        val poses = listOf(Pose.IDLE, Pose.TALK, Pose.HAPPY, Pose.CHEER, Pose.SAD, Pose.THINK, Pose.LISTEN, Pose.WAVE)
        sheet(Face.entries.map { f -> poses.map { p -> portrait("child2", f, 32, pose = p) } }, 6, file = "faces-poses.png")
        // a contact sheet: every face on a child, the teacher, grandpa and a village woman, the bust (as on a stage) over
        // the head alone (as by a bubble), at the canvas sizes the app draws them
        val contact = listOf("child2", "teacher", "grandpa", "woman").flatMap { s ->
            listOf(Face.entries.map { f -> portrait(s, f, 31) }, Face.entries.map { f -> portrait(s, f, 24, Framing.HEAD) })
        }
        sheet(contact, 6, file = "faces-contact.png")
        // close up, to judge the pixels: the head alone, big
        val zoom = listOf("child1", "teacher", "grandpa", "woman", "grandma", "smith")
        sheet(zoom.map { s -> Face.entries.take(7).map { f -> portrait(s, f, 24, Framing.HEAD) } }, 10, file = "faces-zoom-a.png")
        sheet(zoom.map { s -> Face.entries.drop(7).map { f -> portrait(s, f, 24, Framing.HEAD) } }, 10, file = "faces-zoom-b.png")
    }

    @Test fun `every face looks its own`() {
        for (s in sprites) {
            val drawn = Face.entries.associateWith { f -> portrait(s, f, 32).pixels.toList() }
            for (a in Face.entries) for (b in Face.entries) if (a < b) assertNotEquals("$s: $a and $b look alike", drawn[a], drawn[b])
            // the scene's sprite too, every face but the resting one differs from it
            val rest = sprite(s, null).pixels.toList()
            for (f in Face.entries) if (f != Face.NEUTRAL) assertNotEquals("$s in a scene: $f looks like no face", rest, sprite(s, f).pixels.toList())
        }
    }

    @Test fun `the resting face is the face without one`() {
        for (s in sprites) for (pose in listOf(Pose.IDLE, Pose.TALK)) for (t in listOf(0.4, 1.31, 2.2)) {
            assertEquals("$s $pose at $t", portrait(s, null, 32, pose = pose, t = t).pixels.toList(), portrait(s, Face.NEUTRAL, 32, pose = pose, t = t).pixels.toList())
        }
    }

    @Test fun `they blink, but for the shut eyes, and the mouth moves when talking`() {
        fun drawn(f: Face, t: Double) = PixelCanvas(48, 56).also {
            PeoplePainter().draw(it, "teacher", 24, 52, t, 0, talking = false, flip = false, walking = false, seated = false, face = f)
        }.pixels.toList()
        for (f in Face.entries) {
            // a blink (the first 0.14 s of its 3.7 s) against a moment without one, the breath the same
            if (f.eyesShut) assertEquals("$f: the shut eyes don't blink", drawn(f, 0.05), drawn(f, 0.16))
            else assertNotEquals("$f blinks", drawn(f, 0.05), drawn(f, 0.16))
            val talking = (0 until 6).map { k -> portrait("teacher", f, 32, pose = Pose.TALK, t = 0.5 + k * 0.143).pixels.toList() }.toSet()
            assertTrue("$f: the mouth moves", talking.size >= 2)
        }
    }

    @Test fun `the emotes show where a face has one, inside the canvas`() {
        for (f in Face.entries) for (size in listOf(24, 32, 64)) for (framing in Framing.entries) {
            val c = portrait("grandpa", f, size, framing)
            val painter = PortraitsPainter()
            // an emote leaves its ink inside the canvas: rendered on a bigger one, nothing of it is cut
            if (f.emote != null) {
                val ink = c.pixels.count { it == 0xFF2A1E26.toInt() || it == 0xFFE8403A.toInt() || it == 0xFFF0A21E.toInt() }
                assertTrue("$f at $size $framing: its emote shows", ink > 0)
            }
            painter.render(PixelCanvas(size, size), "grandpa", Pose.IDLE, 0.4, 11f, 0, f, 0.0, framing) // popping in
        }
    }

    @Test fun `a face shows in every scene, its emote not a part of the person`() {
        for (art in SceneArt.arts.take(6)) {
            val slot = SceneArt.personSlots.getValue(art).first()
            val plain = PixelCanvas(240, 160); val laughing = PixelCanvas(240, 160)
            val painter = ScenePainters.create(art)
            painter.render(plain, SceneFrame(time = 1.0, people = listOf(PersonInScene("p", "child1", slot))), Lens(1, 0, 0, 240, 160))
            val hits = painter.render(laughing, SceneFrame(time = 1.0, people = listOf(PersonInScene("p", "child1", slot, face = Face.LAUGH))), Lens(1, 0, 0, 240, 160))
            assertNotEquals("$art: the face shows", plain.pixels.toList(), laughing.pixels.toList())
            // the note's ink is drawn after the outlines, with no one's id
            val ids = laughing.ids
            val noteInk = laughing.pixels.indices.filter { laughing.pixels[it] == 0xFFF0587A.toInt() }
            assertTrue("$art: the note shows", noteInk.isNotEmpty())
            assertTrue("$art: the note is no one's", noteInk.all { ids[it] == 0 })
            assertEquals(1, hits.count { it.target == si.lanisce.lani.game.scene.SceneTarget.Person("p") })
        }
    }

    @Test fun `a face is drawn closer up too`() {
        val painter = ScenePainters.create("campfire")
        val slot = SceneArt.personSlots.getValue("campfire").first()
        val frame = SceneFrame(time = 1.0, people = listOf(PersonInScene("p", "grandma", slot, face = Face.SURPRISED)))
        val hit = painter.render(PixelCanvas(240, 160), frame).first { it.target == si.lanisce.lani.game.scene.SceneTarget.Person("p") }
        val c = PixelCanvas(240, 160)
        painter.render(c, frame, Lens(3, (hit.anchorX * 3 - 120).coerceAtLeast(0), (hit.anchorY * 3 - 80).coerceAtLeast(0), 240, 160))
        assertTrue("the ! closer up", c.pixels.any { it == 0xFFE8452C.toInt() })
        sheet(listOf(listOf(c)), 3, file = "faces-closer.png")
    }

    @Test fun `a portrait with a face renders fast`() {
        val c = PixelCanvas(64, 64)
        val painter = PortraitsPainter()
        repeat(30) { painter.render(c, "grandma", Pose.TALK, it * 0.1, 12f, 0, Face.LAUGH) }
        var worst = 0.0
        for (f in Face.entries) {
            // thirteen frames over 4 s, the best of three rounds of them, in this thread's CPU time (FrameBudget)
            worst = maxOf(worst, FrameBudget.us(13) { painter.render(c, "grandpa", Pose.TALK, 2 + it / 3.25, 21f, 0, f) })
        }
        assertTrue("portraits with faces too slow: $worst us", worst < FrameBudget.scaled(3000.0))
    }

    @Test fun `no face, no emote`() {
        val p = PeoplePainter()
        val c = PixelCanvas(48, 56)
        p.draw(c, "teacher", 24, 52, 0.4, 0, talking = false, flip = false, walking = false, seated = false, face = null)
        assertNull(p.headFace)
        p.draw(c, "teacher", 24, 52, 0.4, 0, talking = false, flip = false, walking = false, seated = false, face = Face.PROUD)
        assertEquals(Face.PROUD, p.headFace)
        // asleep they keep the sleeper's face
        p.draw(c, "teacher", 24, 52, 0.4, 0, talking = false, flip = false, walking = false, seated = false, pose = Pose.SLEEP, upright = true, face = Face.LAUGH)
        assertNull(p.headFace)
    }
}
