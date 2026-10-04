package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
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
 * The rooms by their building's level (companion/SCENES.md, "What the village has built"): each at levels 1 to 3, by day
 * and in the evening, with its people, rendered side by side for review (build/scene-snapshots/rooms: a sheet per room,
 * and each level alone, closer up), and checked: every level looks different, only what the level has is drawn and
 * tappable, and the third level is the top one.
 */
class RoomLevelsRenderTest {
    private val dir = File("build/scene-snapshots/rooms").apply { mkdirs() }

    /** The rooms: art, the building type it is seen in, and who stands at its spots in the previews. */
    private val rooms = listOf(
        Triple("kitchen", BuildingType.HUT, listOf("grandma", "aunt", "child2")),
        // the rooms of the houses (Homes): Stari Janez's living room, France's workshop, Marko's cellar, Vida and Nejc's attic
        Triple("livingroom", BuildingType.HOUSE, listOf("grandpa", "child2", "farmer")),
        Triple("workshop", BuildingType.HOUSE, listOf("farmer", "child3", "grandpa")),
        Triple("cellar", BuildingType.HOUSE, listOf("winemaker", "innkeeper", "farmer")),
        Triple("attic", BuildingType.HOUSE, listOf("child1", "innkeeper", "woman")),
        Triple("smithy", BuildingType.SMITHY, listOf("smith", "child1", "winemaker")),
        Triple("school", BuildingType.SCHOOL, listOf("teacher", "child3", "child2", "grandpa")),
    )

    private fun frame(art: String, level: Int, hour: Float, sprites: List<String>, people: Boolean = true) = SceneFrame(
        time = 3.0, hour = hour, month = 6, objects = SceneArt.objects.getValue(art).toSet(),
        world = SceneWorld.room(level, rooms.first { it.first == art }.second),
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, s -> PersonInScene("p-$s", sprites[k % sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    @Test fun `render every room at levels 1 to 3`() {
        for ((art, _, sprites) in rooms) {
            val s = 2; val label = 16
            val hours = listOf("day" to 10.5f, "evening" to 20f)
            val img = BufferedImage(240 * s * 3, (160 * s + label) * hours.size, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((row, hh) in hours.withIndex()) for (level in 1..3) {
                val c = render(art, frame(art, level, hh.second, sprites))
                val oy = row * (160 * s + label)
                for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB((level - 1) * 240 * s + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
                g.color = Color.WHITE
                g.drawString("$art, level $level, ${hh.first}", (level - 1) * 240 * s + 4, oy + 12)
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "$art-levels.png"))
            // each level closer up, without people: the room itself
            for (level in 1..3) write(render(art, frame(art, level, 10.5f, sprites, people = false), 3), File(dir, "$art-L$level.png"), 1)
        }
    }

    @Test fun `each level differs, draws only what it has, and the third is the top`() {
        for ((art, type, sprites) in rooms) {
            val pics = (1..3).map { render(art, frame(art, it, 10.5f, sprites, people = false)).pixels.toList() }
            assertNotEquals("$art: level 2 looks like level 1", pics[0], pics[1])
            assertNotEquals("$art: level 3 looks like level 2", pics[1], pics[2])
            assertEquals("$art: the top level is the third's", pics[2], render(art, frame(art, 5, 10.5f, sprites, people = false)).pixels.toList())
            for (level in 1..3) {
                val w = SceneWorld.room(level, type)
                val hits = ScenePainters.create(art).render(PixelCanvas(240, 160), frame(art, level, 10.5f, sprites, people = false))
                for (slot in SceneArt.objects.getValue(art)) {
                    val n = hits.count { it.target == SceneTarget.Thing(slot) }
                    assertEquals("$art/$slot at level $level", if (SceneFixtures.there(art, slot, w)) 1 else 0, n)
                }
            }
        }
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
