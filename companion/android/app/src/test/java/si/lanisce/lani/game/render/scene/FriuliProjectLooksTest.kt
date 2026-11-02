package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Friuli's look of the places its projects are built at, as its steps are played (docs/plans/05-built/friuli-projects-a.md):
 * the greasy pole in the wood and on the square, the bridge and the mill on the stream, step by step, the bee meadow by the
 * bee garden, and the finished places (the mill inside, the bocce court, the ancona, the vineyard). Each beside the default
 * (Primorska's) picture at each step of its project (build/scene-snapshots/looks-projects/friuli-<art>-….png); and checked:
 * each step draws more than the one before, and every thing the picture has is there to tap.
 */
class FriuliProjectLooksTest {
    private val dir = File("build/scene-snapshots/looks-projects").apply { mkdirs() }

    /** A row of a sheet: its label and its frame in a culture. */
    private class Row(val label: String, val frame: (String) -> SceneFrame)

    /** An art of this round, the project whose steps its picture draws (none: the place of a finished project), who stands at its spots. */
    private class Art(val art: String, val project: String?, val sprites: List<String>, val type: BuildingType? = null) {
        /** Its sheets' name: the art, and the project when the art draws more than one. */
        val key: String get() = if (project != null && art == "stream") "$art-$project" else art
    }

    private val arts = listOf(
        Art("square", "mlaj", listOf("grandpa", "teacher", "child1", "child2", "child3", "shepherd")),
        Art("stream", "most", listOf("farmer", "shepherd", "smith")),
        Art("stream", "mlin", listOf("farmer", "shepherd", "smith")),
        Art("mill", null, listOf("farmer", "child3", "grandma")),
        Art("bocce", null, listOf("grandpa", "farmer", "winemaker")),
        Art("chapel", null, listOf("aunt", "grandma", "child2")),
        Art("hills", null, listOf("winemaker", "child2", "child3")),
        Art("apiary", "cebelji_travnik", listOf("beekeeper", "grandma", "child2"), BuildingType.BEEHIVE),
        Art("forest", "mlaj", listOf("shepherd", "beekeeper", "grandpa")),
    )

    private fun world(culture: String, projects: Map<String, Int>?, type: BuildingType? = null) =
        SceneWorld(culture = culture, projects = projects, here = type?.let { Building("here", it, 0, level = 5) })

    private fun frame(
        a: Art, culture: String, hour: Float = 10.5f, month: Int = 5, projects: Map<String, Int>? = null, people: Boolean = true,
        fx: Map<String, Float> = emptyMap(),
    ) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(a.art).toSet(), world = world(culture, projects, a.type), fx = fx,
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(a.art).mapIndexed { k, s -> PersonInScene("p-$s", a.sprites[k % a.sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    private fun sheet(name: String, art: String, rows: List<Row>, k: Int = 1) {
        val s = if (k > 1) k else 2; val label = 16
        val cs = listOf("primorska", "friuli")
        val cw = 240 * k; val ch = 160 * k
        val img = BufferedImage(cw * s / k * cs.size, (ch * s / k + label) * rows.size, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for ((row, r) in rows.withIndex()) for ((col, culture) in cs.withIndex()) {
            val c = render(art, r.frame(culture), k)
            val oy = row * (ch * s / k + label)
            val ow = cw * s / k
            for (y in 0 until ch * s / k) for (x in 0 until ow) img.setRGB(col * ow + x, oy + label + y, c.pixels[(y * k / s) * cw + x * k / s])
            g.color = Color.WHITE
            g.drawString("$art, $culture, ${r.label}", col * ow + 4, oy + 12)
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, name))
    }

    /** The project's stages as rows: nothing done, each step done, by day in May. */
    private fun stageRows(a: Art, month: Int = 5): List<Row> {
        val p = a.project ?: return emptyList()
        val n = SceneWorld.ALL.stages(p)
        return (0..n).map { st -> Row("$p $st/$n") { c -> frame(a, c, month = month, projects = mapOf(p to st), people = false) } }
    }

    @Test fun `render the projects' places beside the default ones`() {
        for (a in arts) {
            if (a.project != null) sheet("friuli-${a.key}-stages.png", a.art, stageRows(a))
            sheet(
                "friuli-${a.key}-day.png", a.art,
                listOf(
                    Row("day, May") { c -> frame(a, c) },
                    Row("evening, October") { c -> frame(a, c, hour = 20f, month = 10) },
                    Row("night, January") { c -> frame(a, c, hour = 23f, month = 1) },
                ),
            )
            sheet(
                "friuli-${a.key}-months.png", a.art,
                listOf(1, 4, 5, 6, 8, 9, 10).map { m -> Row("month $m") { c -> frame(a, c, month = m, people = false) } },
            )
            sheet("friuli-${a.key}-closer.png", a.art, listOf(Row("closer, May") { c -> frame(a, c) }), k = 3)
        }
        val sq = arts.first { it.art == "square" }
        sheet(
            "friuli-square-raising.png", "square",
            listOf(0.2f, 0.6f).map { r -> Row("raising $r") { c -> frame(sq, c, projects = mapOf("mlaj" to 4), fx = mapOf("maypole" to r)) } } +
                listOf(0f, 0.5f, 1f).map { l -> Row("climbing $l") { c -> frame(sq, c, fx = mapOf("climb" to l)) } },
            k = 2,
        )
        val st = arts.first { it.key == "stream-most" }
        sheet("friuli-stream-most-cart.png", "stream", listOf(0f, 0.5f, 0.8f, 1f).map { r -> Row("the first cart $r") { c -> frame(st, c, projects = mapOf("most" to 5), fx = mapOf("cart" to r)) } }, k = 2)
        val fo = arts.first { it.art == "forest" }
        sheet("friuli-forest-fell.png", "forest", listOf(0f, 0.5f, 1f).map { r -> Row("the fir felled $r") { c -> frame(fo, c, projects = mapOf("mlaj" to 0), fx = mapOf("fell" to r)) } } + (0..2).map { n -> Row("mlaj $n/5") { c -> frame(fo, c, projects = mapOf("mlaj" to n)) } }, k = 2)
    }

    @Test fun `the default culture's pictures don't change`() {
        for (a in arts) for (st in listOfNotNull(null, a.project?.let { 1 }, a.project?.let { 3 })) {
            val projects = st?.let { mapOf(a.project!! to it) }
            val p = render(a.art, frame(a, "primorska", projects = projects)).pixels.toList()
            val none = render(a.art, frame(a, "nowhere", projects = projects)).pixels.toList()
            assertEquals("${a.art} ($st): the default culture draws as a culture without a look", none, p)
        }
    }

    @Test fun `Friuli draws each step of its projects its own way, each more than the one before`() {
        val failed = mutableListOf<String>()
        // the projects whose every step the picture draws (the maypole on the green, the bridge and the mill on the beck)
        for (a in arts.filter { it.key in STEPPED }) {
            val p = a.project ?: continue
            val n = SceneWorld.ALL.stages(p)
            var prev: List<Int>? = null
            for (st in 0..n) {
                val f = { c: String -> frame(a, c, projects = mapOf(p to st), people = false) }
                val look = render(a.art, f("friuli")).pixels.toList()
                // the pegs and the line of a first step are the same everywhere
                if (st > 1 && look == render(a.art, f("primorska")).pixels.toList()) failed += "${a.art}: step $st looks as Primorska's"
                if (prev != null && prev == look) failed += "${a.art}: step $st looks as the one before"
                prev = look
            }
        }
        assertEquals(emptyList<String>(), failed)
    }

    @Test fun `every thing Friuli's picture has is there to tap, at every detail`() {
        val failed = mutableListOf<String>()
        for (a in arts) for (k in 1..3) {
            val f = frame(a, "friuli")
            val c = PixelCanvas(240 * k, 160 * k)
            val hits = ScenePainters.create(a.art).render(c, f, Lens(k, 0, 0, 240, 160))
            val want = SceneArt.objects.getValue(a.art)
                .filter { slot -> slot !in si.lanisce.lani.game.scene.Wildlife.all(a.art) && si.lanisce.lani.game.scene.SceneFixtures.there(a.art, slot, f.world) }
            for (slot in want) if (hits.none { it.target == SceneTarget.Thing(slot) }) failed += "${a.art}: no ${slot} to tap (detail $k)"
        }
        assertEquals(emptyList<String>(), failed)
    }

    private companion object {
        /** The arts that draw each step of their project ([Art.key]). */
        val STEPPED = setOf("square", "stream-most", "stream-mlin")
    }
}
