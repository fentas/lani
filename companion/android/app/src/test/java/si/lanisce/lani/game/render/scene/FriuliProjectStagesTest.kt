package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Friuli's projects that a scene draws step by step, in Friuli's look (docs/plans/05-built/friuli-projects-b.md; companion/SCENES.md
 * "Project steps", "Culture looks"): the fountain with its pine cone and the clock in the free-standing campanile on the square,
 * the big hay barn in the field. Each stage is rendered for review (build/scene-snapshots/looks/friuli-stages-<project>.png: a
 * row of its stages, closer up); and each step whose work shows at the tower, on the square or in the field looks different from
 * the one before, at every detail.
 */
class FriuliProjectStagesTest {
    private val dir = File("build/scene-snapshots/looks").apply { mkdirs() }
    private val w = 240
    private val h = 160

    private class Project(val id: String, val art: String, val type: BuildingType, val unseen: Set<Int> = emptySet())

    private val projects = listOf(
        Project("vodnjak_na_trgu", "square", BuildingType.WELL),
        // its size worked out at school, the gears in the smithy, the hands in the workshop, the face at school: the tower shows them
        // once lifted in; the clock going moves only its hands, a few pixels at the scene's own size
        Project("mestna_ura", "square", BuildingType.WELL, setOf(1, 3, 4, 5, 7)),
        Project("toplar", "field", BuildingType.FIELD),
    )

    private fun frame(p: Project, stage: Int): SceneFrame {
        val world = SceneWorld(culture = "friuli", here = Building("here", p.type, 0, level = Catalog.MAX_LEVEL), projects = mapOf(p.id to stage))
        return SceneFrame(time = 3.0, hour = 11f, month = 7, objects = SceneArt.objects.getValue(p.art).toSet(), fx = emptyMap(), world = world, wild = emptySet())
    }

    private fun render(p: Project, stage: Int, k: Int): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(p.art).render(c, frame(p, stage), Lens(k, 0, 0, w, h))
        return c
    }

    @Test fun `each step of Friuli's fountain, clock and big hay barn shows on its picture`() {
        for (p in projects) {
            val n = SceneWorld().stages(p.id)
            for (k in 1..2) {
                var before = render(p, 0, k).pixels
                for (st in 1..n) {
                    val now = render(p, st, k).pixels
                    val d = now.indices.count { now[it] != before[it] }
                    if (st !in p.unseen) assertTrue("${p.id}: step $st at detail $k looks the same as the one before ($d pixels)", d >= 15 * k * k)
                    before = now
                }
            }
        }
    }

    @Test fun `render the stages of Friuli's projects`() {
        for (p in projects) {
            val n = SceneWorld().stages(p.id)
            val k = 2
            val cols = 4
            val rows = (n + 1 + cols - 1) / cols
            val sheet = BufferedImage(cols * w * k, rows * h * k, BufferedImage.TYPE_INT_RGB)
            for (st in 0..n) {
                val c = render(p, st, k)
                val x0 = (st % cols) * w * k
                val y0 = (st / cols) * h * k
                for (y in 0 until c.height) for (x in 0 until c.width) sheet.setRGB(x0 + x, y0 + y, c.pixels[y * c.width + x])
            }
            ImageIO.write(sheet, "png", File(dir, "friuli-stages-${p.id}.png"))
        }
    }
}
