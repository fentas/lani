package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.Sky
import si.lanisce.lani.game.scene.Stance
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Stage directions in the pictures (companion/SCENES.md, "Stage directions"): snapshots for review in
 * build/scene-snapshots/stage.
 */
class StageRenderTest {
    private val dir = File("build/scene-snapshots/stage").apply { mkdirs() }
    private val bw = 270; private val bh = 297

    private fun frame(art: String, people: List<PersonInScene>, time: Double = 3.0, hour: Float = 15f) =
        SceneFrame(time = time, hour = hour, month = 7, objects = SceneArt.objects.getValue(art).toSet(), people = people)

    private fun render(art: String, f: SceneFrame, k: Int = 1): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(bw * k, bh * k)
        return c to ScenePainters.create(art).render(c, f, Lens(k, 0, 0, bw, bh))
    }

    private fun write(c: PixelCanvas, name: String, scale: Int = 4) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    private val zala = PersonInScene("zala", "child2", "stove")
    private val janez = PersonInScene("janez", "grandpa", "table")
    private val extra = emptyList<Pair<String, List<PersonInScene>>>()

    /** Every spot of [art]'s stage in each of its poses, Zala there; and on her way to each from the first. */
    private fun placements(art: String): List<Pair<String, PersonInScene>> {
        val spots = SceneArt.stage.getValue(art)
        val first = spots.first().name
        return spots.flatMap { s -> s.poses.map { pose -> "${s.name}/$pose" to zala.copy(slot = s.name, stance = Stance.of(pose)!!) } } +
            spots.drop(1).flatMap { s ->
                listOf(0.2f, 0.8f, 1.6f).map { age -> "$first→${s.name}@$age" to zala.copy(slot = s.name, stance = Stance.of(s.poses.last())!!, from = first, moveAge = age) }
            }
    }

    @Test fun `hidden, they can't be tapped for their card, peeking or standing they can, what hides them can`() {
        for (art in SceneArt.stage.keys) for (s in SceneArt.stage.getValue(art)) for (pose in s.poses) {
            val (_, hits) = render(art, frame(art, listOf(zala.copy(slot = s.name, stance = Stance.of(pose)!!))))
            val person = hits.any { it.target == SceneTarget.Person("zala") }
            assertEquals("$art/${s.name}/$pose: Zala tappable", pose != "hide", person)
            s.cover?.let { cover -> assertTrue("$art/${s.name}/$pose: the $cover is there to tap", hits.any { it.target == SceneTarget.Thing(cover) }) }
        }
    }

    @Test fun `a hiding place looks different with someone in it, and a peek differs from the hidden`() {
        for (art in SceneArt.stage.keys) for (s in SceneArt.stage.getValue(art)) {
            if ("hide" !in s.poses) continue
            val (empty, _) = render(art, frame(art, emptyList()))
            val (hidden, _) = render(art, frame(art, listOf(zala.copy(slot = s.name, stance = Stance.HIDE))))
            val (peek, _) = render(art, frame(art, listOf(zala.copy(slot = s.name, stance = Stance.PEEK))))
            val clue = empty.pixels.indices.count { empty.pixels[it] != hidden.pixels[it] }
            val more = hidden.pixels.indices.count { hidden.pixels[it] != peek.pixels[it] }
            assertTrue("$art/${s.name}: a clue shows ($clue px)", clue in 12..2500)
            assertTrue("$art/${s.name}: peeking shows more of them ($more px)", more > 40)
        }
    }

    @Test fun `the spot is found where someone stands or sits there`() {
        for (art in SceneArt.stage.keys) {
            val p = ScenePainters.create(art)
            p.render(PixelCanvas(bw, bh), frame(art, listOf(zala)))
            for (s in SceneArt.stage.getValue(art)) {
                val pose = if ("stand" in s.poses) Stance.STAND else Stance.of(s.poses.first())!!
                val (_, hits) = render(art, frame(art, listOf(zala.copy(slot = s.name, stance = pose))))
                val h = hits.first { it.target == SceneTarget.Person("zala") }
                assertEquals("$art/${s.name}: the spot where whoever is there is", s.name, p.spotAt((h.left + h.right) / 2f, (h.top + h.bottom) / 2f, 1f))
            }
        }
    }

    @Test fun `a window of the picture is that part of the whole picture, and the hits stay put, with everyone on the stage`() {
        val failed = ArrayList<String>()
        for (art in SceneArt.stage.keys) for ((name, p) in placements(art)) {
            val f = frame(art, listOf(p, janez), hour = 21.5f, time = 3.4)
            val base = ScenePainters.create(art).render(PixelCanvas(bw, bh), f).associateBy { it.target }
            for (k in 2..Lens.MAX_DETAIL) {
                val (all, hits) = render(art, f, k)
                for (h in hits) {
                    val b = base[h.target] ?: run { failed.add("$art $name d$k: ${h.target} not at detail 1"); null } ?: continue
                    val off = maxOf(abs(h.left - b.left), abs(h.top - b.top), abs(h.right - b.right), abs(h.bottom - b.bottom))
                    if (off > 3) failed.add("$art $name d$k: ${h.target} moved $off px")
                }
                val ww = 150; val wh = 110
                val x0 = (bw * k - ww) / 2 + 7; val y0 = (bh * k - wh) / 2 - 5
                val win = PixelCanvas(ww, wh)
                ScenePainters.create(art).render(win, f, Lens(k, x0, y0, bw, bh))
                var bad = 0
                for (y in 1 until wh - 1) for (x in 1 until ww - 1) if (win.pixels[y * ww + x] != all.pixels[(y0 + y) * all.width + x0 + x]) bad++
                if (bad > ww * wh / 500) failed.add("$art $name d$k: the window differs in $bad px")
            }
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `snapshots of the tent's stage, Luka coming in out of the rain`() {
        val art = "tent"
        val luka = PersonInScene("luka", "shepherd", "door")
        val storm = Sky(rain = 0.9f, gloom = 0.85f, wind = 0.3f, lightning = true)
        val walk = listOf(0.3f, 0.7f, 1.1f, 1.5f).mapIndexed { i, age -> "0${i + 1}-walk-in-$age" to listOf(luka.copy(slot = "lantern", stance = Stance.SIT, from = "door", moveAge = age)) }
        val views = listOf("00-at-the-door" to listOf(luka)) + walk + listOf(
            "05-by-the-lantern" to listOf(luka.copy(slot = "lantern", stance = Stance.SIT)),
            "06-standing-by-the-lantern" to listOf(luka.copy(slot = "lantern")),
        )
        for ((name, people) in views) {
            val f = frame(art, people, hour = 16f).copy(sky = storm, fx = mapOf("lantern" to 1f))
            val (c, _) = render(art, f)
            write(c, "$art-$name")
            val k = 3; val box = intArrayOf(60, 90, 110, 90)
            val w = PixelCanvas(box[2] * k, box[3] * k)
            ScenePainters.create(art).render(w, f, Lens(k, box[0] * k, box[1] * k, bw, bh))
            write(w, "$art-$name-closer", 2)
        }
    }

    @Test fun `in the tent no rain falls on Luka by the lantern, nor on his way in`() {
        val art = "tent"
        val luka = PersonInScene("luka", "shepherd", "lantern", stance = Stance.SIT)
        val storm = Sky(rain = 1f, gloom = 0.6f)
        fun streaks(at: PersonInScene): Int {
            // what the weather changed on Luka's own pixels: the same frames dry and in the rain
            var n = 0
            for (time in listOf(3.0, 3.3, 3.7, 4.2)) {
                val p = ScenePainters.create(art)
                val dry = PixelCanvas(bw, bh)
                val h = p.render(dry, frame(art, listOf(at), time = time)).first { it.target == SceneTarget.Person("luka") }
                val (wet, _) = render(art, frame(art, listOf(at), time = time).copy(sky = storm))
                for (y in h.top until h.bottom) for (x in h.left until h.right) {
                    if (p.targetAt(dry, x, y, 0) != SceneTarget.Person("luka")) continue
                    val a = dry.pixels[y * bw + x]; val b = wet.pixels[y * bw + x]
                    // the rain's streaks are lighter than what they fall over (the gloom only darkens)
                    if (si.lanisce.lani.game.render.Col.b(b) > si.lanisce.lani.game.render.Col.b(a) + 10) n++
                }
            }
            return n
        }
        assertEquals("no rain on Luka by the lantern", 0, streaks(luka))
        for (age in listOf(0.4f, 0.9f, 1.4f)) assertEquals("no rain nor drips on Luka coming in ($age s)", 0, streaks(luka.copy(from = "door", moveAge = age)))
    }

    @Test fun `the showcase frames, hide-and-seek in the living room and Luka coming into the tent`() {
        val out = File(dir, "showcase").apply { mkdirs() }
        fun shoot(art: String, name: String, people: List<PersonInScene>, box: IntArray, f: (SceneFrame) -> SceneFrame = { it }) {
            val frame = f(frame(art, people, hour = 16f))
            val (c, _) = render(art, frame)
            val whole = BufferedImage(c.width * 3, c.height * 3, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until c.height * 3) for (x in 0 until c.width * 3) whole.setRGB(x, y, c.pixels[(y / 3) * c.width + x / 3])
            ImageIO.write(whole, "png", File(out, "$name.png"))
            val k = 3
            val w = PixelCanvas(box[2] * k, box[3] * k)
            ScenePainters.create(art).render(w, frame, Lens(k, box[0] * k, box[1] * k, bw, bh))
            val img = BufferedImage(w.width * 2, w.height * 2, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until w.height * 2) for (x in 0 until w.width * 2) img.setRGB(x, y, w.pixels[(y / 2) * w.width + x / 2])
            ImageIO.write(img, "png", File(out, "$name-closer.png"))
        }
        val room = intArrayOf(70, 80, 140, 100)
        val zalaTalks = zala.copy(talking = true)
        shoot("livingroom", "hide-1-before", listOf(zala.copy(pose = si.lanisce.lani.game.scene.Pose.WAVE)), room)
        shoot("livingroom", "hide-2-hidden-behind-the-door", listOf(zalaTalks.copy(slot = "behind-door", stance = Stance.HIDE)), room)
        shoot("livingroom", "hide-3-peeking-out-at-a-wrong-guess", listOf(zala.copy(slot = "behind-door", stance = Stance.PEEK, pose = si.lanisce.lani.game.scene.Pose.HAPPY)), room)
        shoot("livingroom", "hide-4-found-popping-out", listOf(zala.copy(slot = "door", from = "behind-door", fromStance = Stance.HIDE, moveAge = 0.22f)), room)
        shoot("livingroom", "hide-5-found", listOf(zala.copy(slot = "door", pose = si.lanisce.lani.game.scene.Pose.HAPPY)), room, { it.copy(fx = mapOf("dog" to 1f)) })
        shoot("livingroom", "hide-6-hidden-under-the-table", listOf(zala.copy(slot = "under-table", stance = Stance.HIDE)), room)
        shoot("livingroom", "hide-7-peeking-from-under-the-table", listOf(zala.copy(slot = "under-table", stance = Stance.PEEK)), room)
        // a house at level 1: a bare table, a plastered stove, the same hiding places
        val bare = { f: SceneFrame -> f.copy(world = si.lanisce.lani.game.scene.SceneWorld.room(1, si.lanisce.lani.game.BuildingType.HOUSE)) }
        shoot("livingroom", "hide-8-level-1-under-the-bare-table", listOf(zala.copy(slot = "under-table", stance = Stance.HIDE)), room, bare)
        shoot("livingroom", "hide-9-level-1-behind-the-door", listOf(zala.copy(slot = "behind-door", stance = Stance.HIDE)), room, bare)
        val tent = intArrayOf(55, 85, 130, 100)
        val luka = PersonInScene("luka", "shepherd", "door")
        val rain = { f: SceneFrame -> f.copy(sky = Sky(rain = 0.85f, gloom = 0.9f, wind = 0.35f, lightning = true)) }
        val lit = { f: SceneFrame -> rain(f).copy(fx = mapOf("lantern" to 1f)) }
        shoot("tent", "luka-1-at-the-tent-in-the-rain", listOf(luka.copy(talking = true)), tent, rain)
        shoot("tent", "luka-2-walking-in", listOf(luka.copy(slot = "lantern", stance = Stance.SIT, from = "door", moveAge = 0.6f)), tent, rain)
        shoot("tent", "luka-3-walking-in", listOf(luka.copy(slot = "lantern", stance = Stance.SIT, from = "door", moveAge = 1.3f)), tent, rain)
        shoot("tent", "luka-4-by-the-lantern", listOf(luka.copy(slot = "lantern", stance = Stance.SIT)), tent, lit)
    }

    @Test fun `snapshots of the living room's stage`() {
        val art = "livingroom"
        val views = listOf(
            "00-before" to listOf(zala, janez),
            "01-under-table-hide" to listOf(zala.copy(slot = "under-table", stance = Stance.HIDE), janez),
            "02-under-table-peek" to listOf(zala.copy(slot = "under-table", stance = Stance.PEEK)),
            "03-under-table-out" to listOf(zala.copy(slot = "under-table"), janez),
            "04-door-hide" to listOf(zala.copy(slot = "behind-door", stance = Stance.HIDE), janez),
            "05-door-peek" to listOf(zala.copy(slot = "behind-door", stance = Stance.PEEK), janez),
            "06-door-out" to listOf(zala.copy(slot = "behind-door"), janez),
            "10-bench-sit" to listOf(zala.copy(slot = "bench", stance = Stance.SIT), janez),
            "11-walk" to listOf(zala.copy(slot = "table", from = "stove", moveAge = 0.6f), janez.copy(slot = "door", from = "table", moveAge = 0.9f)),
            "12-slip" to listOf(zala.copy(slot = "under-table", stance = Stance.HIDE, from = "stove", moveAge = 0.2f), janez),
            "13-pop" to listOf(zala.copy(slot = "door", from = "behind-door", fromStance = Stance.HIDE, moveAge = 0.25f), janez),
        )
        for ((name, people) in views + extra) {
            val (c, _) = render(art, frame(art, people))
            write(c, "$art-$name")
            // closer looks at detail 3: the door, the table, the chest
            for ((where, box) in listOf("door" to intArrayOf(150, 85, 64, 70), "table" to intArrayOf(76, 112, 64, 60))) {
                val k = 3
                val w = PixelCanvas(box[2] * k, box[3] * k)
                ScenePainters.create(art).render(w, frame(art, people), Lens(k, box[0] * k, box[1] * k, bw, bh))
                write(w, "$art-$name-$where", 2)
            }
        }
    }
}
