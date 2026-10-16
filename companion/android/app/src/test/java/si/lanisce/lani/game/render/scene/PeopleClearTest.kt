package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.ISpy
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.Wildlife
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.villagers.Villager
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * Where people stand in a scene, nothing of the picture is drawn over their head (companion/SCENES.md, "Where people
 * stand"): at every person spot of every art (where a scene's own people stand, and where a child of the village who
 * comes by for «Vidim, vidim» does, ISpy.host), each of the village's sprites, by day and at night, with everything built
 * and with nothing yet, every thing and wild animal there, what the painter draws after them in its order leaves the top
 * of their sprite alone (the head and the shoulders: [HEAD] of its height). Something behind them drawn after them would
 * cross it: the organ loft's post ran down through whoever stood by the church's door (2026-10-16), the attic's lamp hung
 * before a tall hat. Snapshots of the church's door and the attic's bed in build/scene-snapshots/people-clear.
 */
class PeopleClearTest {
    private val dir = File("build/scene-snapshots/people-clear").apply { mkdirs() }

    /** The part of a sprite from its top that nothing may be drawn over: the head and the shoulders of the chibi sprites. */
    private val HEAD = 0.4f

    /**
     * The spots where someone stands behind something by design, its top as high as their shoulders: Jože up in the high
     * seat behind its front rail; Zala behind the honey table, the jars at her chin.
     */
    private val behind = setOf("forest/highseat", "apiary/table")

    /** The sprites who stand in a scene: the village's (Whereabouts.village), not the babies (carried) nor the strangers at the road. */
    private val sprites = SceneArt.people - setOf("baby", "pedlar", "pilgrim")

    private class Seen(val canvas: PixelCanvas, val drawn: ArtPainter.Drawn?, val painter: ArtPainter)

    private fun render(art: String, slot: String, sprite: String, hour: Float, world: SceneWorld): Seen =
        render(art, listOf(PersonInScene("p", sprite, slot)), "p", hour, world)

    /** [art] with [people], [who] of them kept ([ArtPainter.keepPeople]). */
    private fun render(art: String, people: List<PersonInScene>, who: String, hour: Float = 15f, world: SceneWorld = SceneWorld.ALL): Seen {
        val p = ScenePainters.create(art) as ArtPainter
        p.keepPeople = true
        val c = PixelCanvas(270, 297)
        val f = SceneFrame(
            time = 3.0, hour = hour, month = 7, objects = SceneArt.objects.getValue(art).toSet(), world = world,
            wild = Wildlife.all(art), people = people,
        )
        p.render(c, f, Lens(1, 0, 0, c.width, c.height))
        return Seen(c, p.drawnPeople.firstOrNull { it.id == who }, p)
    }

    /** What of [d]'s head ([HEAD] of their sprite from its top) was drawn over, in [c]'s pixels. */
    private fun overHead(c: PixelCanvas, d: ArtPainter.Drawn): List<Int> {
        val top = d.at.minOf { it / c.width }; val bottom = d.at.maxOf { it / c.width }
        val line = top + ((bottom - top + 1) * HEAD).toInt()
        return d.covered.filter { it / c.width < line }
    }

    @Test fun `nothing of a scene is drawn over the head of someone at a person spot`() {
        val failed = ArrayList<String>()
        for (art in SceneArt.arts) for (slot in SceneArt.personSlots.getValue(art)) {
            if ("$art/$slot" in behind) continue
            // by day built up and not yet (a room at its first level, the open hearth), at night with the lamps lit
            for (sprite in sprites) for ((hour, wn, world) in listOf(Triple(15f, "all", SceneWorld.ALL), Triple(15f, "none", SceneWorld.NONE), Triple(22.5f, "all", SceneWorld.ALL))) {
                val s = render(art, slot, sprite, hour, world)
                val d = s.drawn
                if (d == null) { failed += "$art/$slot: $sprite not drawn"; continue }
                val over = overHead(s.canvas, d)
                if (over.isEmpty()) continue
                // what lies over them: the thing tapped there, else scenery (a prop)
                val what = over.groupingBy { i -> s.painter.targetAt(s.canvas, i % s.canvas.width, i / s.canvas.width, 0)?.let { (it as? SceneTarget.Thing)?.slot ?: it.toString() } ?: "scenery" }.eachCount()
                failed += "$art/$slot: $sprite at $hour h ($wn built) under $what"
            }
        }
        assertTrue("drawn over a head:\n" + failed.joinToString("\n"), failed.isEmpty())
    }

    @Test fun `the child who comes by for I spy stands clear in every scene, its own child away`() {
        val scenes = listOf(File("../../scenes"), File("../scenes"), File("companion/scenes")).first { it.isDirectory }
        val zala = Villager("zala", "Zala", "👧", "child2")
        var church: String? = null
        for (f in scenes.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }) {
            val scene = parseScene(f.readText())
            // the scene's grown-ups in the picture, its children away (Tine not in the church today)
            val drawn = scene.people.filterNot { ISpy.isChild(it.art) }.distinctBy { it.slot }.map { PersonInScene(it.id, it.art, it.slot) }
            val host = ISpy.host(scene, drawn, listOf(zala), 7L, LocalDate.of(2026, 10, 16)) ?: continue
            assertTrue("${scene.id}: ${host.person.slot}", host.comes && host.person.slot in SceneArt.personSlots.getValue(scene.art))
            val s = render(scene.art, drawn + PersonInScene("zala", "child2", host.person.slot), "zala")
            val over = overHead(s.canvas, s.drawn!!)
            assertTrue("${scene.id}: Zala at ${host.person.slot}, ${over.size} px of her head drawn over", "${scene.art}/${host.person.slot}" in behind || over.isEmpty())
            if (scene.id == "v-cerkvi") church = host.person.slot
        }
        assertEquals("in the church Zala comes in by the door", "door", church)
    }

    @Test fun `by the church's door the organ loft is behind them, the attic's lamp behind the tallest`() {
        // Zala (who comes by for I spy) and Tine at the door, Babica: the loft's post no longer down through them, the pews
        // at their feet at most
        for (sprite in listOf("child2", "child3", "grandma")) {
            val s = render("church", "door", sprite, 15f, SceneWorld.ALL)
            val d = s.drawn!!
            assertTrue("church/door $sprite: ${d.covered.size} px of them drawn over", d.covered.size <= d.at.size / 20)
            if (sprite == "child2") write(crop(s.canvas, d), "church-door")
        }
        // the pedlar's krošnja reaches higher than anyone's head: the lamp hangs behind it
        val s = render("attic", "bed", "pedlar", 22.5f, SceneWorld.ALL)
        assertTrue("attic/bed: the lamp over the pedlar", s.drawn!!.covered.isEmpty())
        write(crop(s.canvas, s.drawn), "attic-bed")
    }

    /** [c] round [d], 90 px each way. */
    private fun crop(c: PixelCanvas, d: ArtPainter.Drawn): PixelCanvas {
        val x0 = (d.at.minOf { it % c.width } - 35).coerceAtLeast(0); val y0 = (d.at.minOf { it / c.width } - 35).coerceAtLeast(0)
        val w = minOf(90, c.width - x0); val h = minOf(90, c.height - y0)
        return PixelCanvas(w, h).also { o -> for (y in 0 until h) for (x in 0 until w) o.pixels[y * w + x] = c.pixels[(y0 + y) * c.width + x0 + x] }
    }

    private fun write(c: PixelCanvas, name: String, scale: Int = 6) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }
}
