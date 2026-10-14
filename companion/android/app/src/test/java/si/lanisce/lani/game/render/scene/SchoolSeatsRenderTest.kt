package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainter
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The pupils at their desks in the school (companion/SCENES.md, "Arts"; Jan, 2026-10-14: "the boy in the school is not
 * really sitting at his desk, he is awkwardly standing in the chair below the table"): each sits on the chair behind
 * their desk, the desk in front of them hiding the lap and the legs, the head and the shoulders over its lid, the hands
 * on it. Checked at every detail, by day and in the evening, for the children and a grown-up, and rendered for review
 * (build/scene-snapshots/school: the desks closer up at each detail, and a sheet of who sits there in each pose).
 */
class SchoolSeatsRenderTest {
    private val dir = File("build/scene-snapshots/school").apply { mkdirs() }
    private val bw = 240; private val bh = 160

    private fun frame(people: List<PersonInScene>, hour: Float = 10.5f, time: Double = 3.0) =
        SceneFrame(time = time, hour = hour, month = 6, objects = SceneArt.objects.getValue("school").toSet(), people = people)

    private fun render(p: ScenePainter, f: SceneFrame, k: Int): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(bw * k, bh * k)
        return c to p.render(c, f, Lens(k, 0, 0, bw, bh))
    }

    private val tine = PersonInScene("tine", "child3", "desk1")
    private val zala = PersonInScene("zala", "child2", "desk2")
    private val mojca = PersonInScene("mojca", "teacher", "teacher")
    private val janez = PersonInScene("janez", "grandpa", "door")

    /** The first desk and what lies on it. */
    private val DESK = setOf(SceneTarget.Thing("desk"), SceneTarget.Thing("notebook"), SceneTarget.Thing("pencil"))

    @Test fun `a pupil sits behind the desk, the head over it, the legs hidden by it`() {
        val people = PeoplePainter()
        val failed = ArrayList<String>()
        // the children (all of a child's proportions: Nejc's, Zala's, Tine's, a child born here) and a grown-up at a desk
        val casts = listOf(
            listOf(tine, zala), listOf(tine.copy(art = "child1"), zala.copy(art = "child3")), listOf(tine.copy(art = "child2"), zala.copy(art = "child1")),
            listOf(tine.copy(art = "man"), zala.copy(art = "woman")),
        )
        for (cast in casts) for (hour in listOf(10.5f, 20f)) for (k in 1..3) {
            val p = ScenePainters.create("school")
            val (c, hits) = render(p, frame(cast + mojca + janez, hour), k)
            val desk = hits.first { it.target == SceneTarget.Thing("desk") }
            for (pupil in cast) {
                val at = "${pupil.art} at ${pupil.slot}, ${hour}h, detail $k"
                val h = hits.firstOrNull { it.target == SceneTarget.Person(pupil.id) }
                if (h == null) { failed.add("$at: not there to tap"); continue }
                // what shows of them, from the top of the hair down to the hands on the lid: the seated sprite alone but
                // its lowest rows, the shins and the shoes, hidden by the desk (at least 3 sprite px)
                var top = Int.MAX_VALUE; var bottom = -1
                // (hits are in the scene canvas's pixels, the picture is k times as big)
                val l = h.left * k; val r = h.right * k; val t = h.top * k; val b = h.bottom * k
                for (y in t until b) for (x in l until r) if (p.targetAt(c, x, y, 0) == SceneTarget.Person(pupil.id)) {
                    top = minOf(top, y); bottom = maxOf(bottom, y)
                }
                val alone = seatedRows(people, pupil)
                val shown = (bottom - top + 1).toFloat() / k
                if (shown > alone - 3) failed.add("$at: $shown px of them show, of $alone seated: the legs aren't hidden by the desk")
                if (shown < 12) failed.add("$at: only $shown px of them show: not their head over the desk")
                // down their middle: their own pixels, then right under them the desk (or what lies on it), not the floor
                val cx = (l + r) / 2
                var low = -1
                for (y in t until b) if (p.targetAt(c, cx, y, 0) == SceneTarget.Person(pupil.id)) low = y
                val under = (low + 1..low + 3 * k).map { p.targetAt(c, cx, it, 0) }
                if (pupil.slot == "desk1") {
                    if (under.any { it !in DESK })
                        failed.add("$at: under them is $under, not the desk")
                    // their head over the desk's top
                    if (h.top > desk.top - 8) failed.add("$at: their head isn't over the desk (top ${h.top}, the desk's ${desk.top})")
                } else if (under.any { it is SceneTarget.Person }) failed.add("$at: under them is $under")
                // tapped anywhere on what shows, it is them
                if (p.targetAt(c, cx, (top + low) / 2, 0) != SceneTarget.Person(pupil.id)) failed.add("$at: a tap on them isn't them")
            }
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `the hands lie on the desk when the pose leaves them there, and every pose draws`() {
        val p = ScenePainters.create("school")
        val (empty, _) = render(p, frame(listOf(mojca)), 2)
        for (pose in Pose.entries) {
            val (c, hits) = render(p, frame(listOf(mojca, tine.copy(pose = pose), zala.copy(pose = pose)), time = 0.1), 2)
            for (pupil in listOf(tine, zala)) {
                val h = hits.firstOrNull { it.target == SceneTarget.Person(pupil.id) }
                if (pose == Pose.SLEEP) continue // asleep at a desk they lie on a pillow there, as anywhere
                assertTrue("${pupil.id} $pose drawn", h != null)
            }
            assertTrue("$pose: the picture changes with the pupils in it", !c.pixels.contentEquals(empty.pixels))
        }
        // idle, both hands lie on the lid: pixels of the desk (or of what lies on it) with his arms up in a cheer are his
        val q = ScenePainters.create("school")
        for (k in 1..3) {
            val (idle, _) = render(p, frame(listOf(mojca, tine)), k)
            val (cheer, _) = render(q, frame(listOf(mojca, tine.copy(pose = Pose.CHEER))), k)
            var onDesk = 0
            for (y in 0 until idle.height) for (x in 0 until idle.width) {
                if (p.targetAt(idle, x, y, 0) == SceneTarget.Person("tine") && q.targetAt(cheer, x, y, 0) in DESK) onDesk++
            }
            // two hands of 2 × 2 sprite px at least, and their sleeves
            assertTrue("detail $k: Tine's hands on the desk: $onDesk px", onDesk >= 8 * k * k)
        }
        assertEquals(PeoplePainter().resting(Pose.IDLE, false, 0.0), true to true)
        assertEquals(PeoplePainter().resting(Pose.WAVE, false, 0.0), true to false)
        assertEquals(PeoplePainter().resting(Pose.CHEER, false, 0.0), false to false)
    }

    @Test fun `render the pupils at their desks`() {
        val people = listOf(mojca, tine, zala, janez)
        val p = ScenePainters.create("school")
        for (k in 1..3) {
            val (c, _) = render(p, frame(people), k)
            write(c, "desks-d$k", 200 * k / 3, 130 * k / 3, 340 * k / 3, 220 * k / 3, 9 / k)
        }
        write(render(p, frame(people, 20f), 3).first, "school-evening-d3", scale = 1)
        write(render(p, frame(people), 3).first, "school-day-d3", scale = 1)
        // who sits at the desks (the children, grown-ups), in each pose
        val casts = listOf("child1" to "child2", "child3" to "child1", "man" to "woman", "grandpa" to "grandma")
        val poses = listOf(Pose.IDLE, Pose.TALK, Pose.WAVE, Pose.THINK, Pose.CHEER, Pose.SAD, Pose.HAPPY, Pose.LISTEN)
        val cw = 340; val ch = 220; val label = 14
        for ((row, cast) in casts.withIndex()) {
            val img = BufferedImage(cw * 4, (ch + label) * 2, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((i, pose) in poses.withIndex()) {
                val col = i % 4; val oy = i / 4 * (ch + label)
                val f = frame(listOf(PersonInScene("a", cast.first, "desk1", pose = pose), PersonInScene("b", cast.second, "desk2", pose = pose)), time = 0.1)
                val c = render(p, f, 3).first
                for (y in 0 until ch) for (x in 0 until cw) img.setRGB(col * cw + x, oy + label + y, c.pixels[(130 + y) * c.width + 200 + x])
                g.color = Color.WHITE
                g.drawString("${cast.first} / ${cast.second}: $pose", col * cw + 4, oy + 11)
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "poses-$row-${cast.first}-${cast.second}.png"))
        }
    }

    /** How many rows [p]'s sprite takes sitting, alone (at the frame's time, as the painter seeds it). */
    private fun seatedRows(people: PeoplePainter, p: PersonInScene): Int {
        val c = PixelCanvas(64, 64)
        c.reset(0)
        people.draw(c, p.art, 32, 60, 3.0, Noise.hash(p.id.hashCode(), 7) ushr 1, false, false, walking = false, seated = true, pose = p.pose)
        val rows = (0 until 64).filter { y -> (0 until 64).any { x -> c.pixels[y * 64 + x] != 0 } }
        return rows.last() - rows.first() + 1
    }

    private fun write(c: PixelCanvas, name: String, x0: Int = 0, y0: Int = 0, w: Int = c.width, h: Int = c.height, scale: Int = 2) {
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) img.setRGB(x, y, c.pixels[(y0 + y / scale) * c.width + x0 + x / scale])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }
}
