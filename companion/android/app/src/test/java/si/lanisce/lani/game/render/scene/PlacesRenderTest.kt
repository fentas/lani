package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poked
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.Sky
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The village's places (companion/SCENES.md "Arts"; docs/plans/05-built/places-sl.md): the fire station, the bocce court, the
 * playground, the wayside chapel, the palisade's gate and the lookout tower, each for review in build/scene-snapshots/places
 * (by day, in the evening, at night, the seasons, the weather, every detail, each effect from 0 to 1, the pokes, Friuli's
 * look, the palisade at each level) with a contact sheet of each art, and what they promise: each effect changes the picture,
 * Friuli's look changes it while every thing stays there to tap, and the palisade's gate looks different at every level.
 */
class PlacesRenderTest {
    private val dir = File("build/scene-snapshots/places").apply { mkdirs() }
    private val arts = listOf("firestation", "bocce", "playground", "chapel", "palisade", "lookout")
        .filter { ScenePainters.of(it) !is ScenePainters.Placeholder }

    private fun people(art: String, offset: Int = 0) = SceneArt.personSlots.getValue(art).mapIndexed { k, slot ->
        PersonInScene("p-$slot", listOf("shepherd", "smith", "farmer", "child2", "grandpa", "teacher", "innkeeper", "aunt")[(k + offset) % 8], slot, talking = k == 0)
    }

    private fun frame(
        art: String, hour: Float = 13f, month: Int = 7, time: Double = 3.0, fx: Map<String, Float> = emptyMap(), who: Boolean = true,
        sky: Sky = Sky.CLEAR, pokes: Map<String, Poked> = emptyMap(), world: SceneWorld = SceneWorld.ALL,
    ) = SceneFrame(time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), people = if (who) people(art) else emptyList(), fx = fx, sky = sky, pokes = pokes, world = world)

    private fun render(art: String, f: SceneFrame, w: Int = 240, h: Int = 160, k: Int = 1): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        f.parentFile.mkdirs()
        ImageIO.write(img, "png", f)
    }

    /** A sheet of 240 × 160 frames, [cols] across, each at [scale]. */
    private fun sheet(frames: List<PixelCanvas>, cols: Int, scale: Int, f: File) {
        val rows = (frames.size + cols - 1) / cols
        val img = BufferedImage(240 * scale * cols, 160 * scale * rows, BufferedImage.TYPE_INT_RGB)
        for ((i, c) in frames.withIndex()) {
            val ox = (i % cols) * 240 * scale; val oy = (i / cols) * 160 * scale
            for (y in 0 until 160 * scale) for (x in 0 until 240 * scale) {
                val sx = x / scale; val sy = y / scale
                if (sx < c.width && sy < c.height) img.setRGB(ox + x, oy + y, c.pixels[sy * c.width + sx])
            }
        }
        ImageIO.write(img, "png", f)
    }

    private fun palisadeAt(level: Int) = SceneWorld(here = Building("palisade", BuildingType.PALISADE, 0, level = level))

    @Test fun `render the places for review`() {
        for (art in arts) {
            val d = File(dir, art)
            val friuli = SceneWorld(culture = "friuli")
            val views = listOf(
                "day" to frame(art), "morning" to frame(art, hour = 8.5f, who = false), "evening" to frame(art, hour = 19.6f, month = 9),
                "night" to frame(art, hour = 23f, month = 7), "winter" to frame(art, hour = 11f, month = 1, who = false),
                "autumn" to frame(art, hour = 15.5f, month = 10, who = false), "spring" to frame(art, hour = 9.5f, month = 4, who = false),
                "rain" to frame(art, sky = Sky(rain = 0.8f, gloom = 0.7f, wind = 0.4f)), "fog" to frame(art, hour = 7.5f, month = 11, sky = Sky(fog = 0.7f), who = false),
                "storm-night" to frame(art, hour = 22f, sky = Sky(rain = 1f, gloom = 1f, wind = 0.8f, lightning = true), time = 3.4),
                "friuli-day" to frame(art, world = friuli), "friuli-evening" to frame(art, hour = 19.6f, month = 9, world = friuli),
                "friuli-winter" to frame(art, hour = 11f, month = 1, world = friuli, who = false),
            )
            val main = ArrayList<PixelCanvas>()
            for ((name, f) in views) {
                val c1 = render(art, f)
                main += c1
                write(c1, File(d, "$name-d1.png"), 4)
            }
            for ((name, f) in views.take(4)) for (k in 2..Lens.MAX_DETAIL) write(render(art, f, k = k), File(d, "$name-d$k.png"), if (k == 2) 2 else 1)
            write(render(art, frame(art, hour = 18.5f, month = 9), 270, 297), File(d, "tall-270x297.png"), 3)
            write(render(art, frame(art, hour = 11f, month = 6), 360, 160), File(d, "wide-360x160.png"), 3)
            // the effects, each from 0 to 1
            val fxs = ArrayList<PixelCanvas>()
            for (name in SceneArt.effects.getValue(art)) for (v in listOf(0f, 0.35f, 0.7f, 1f)) {
                val c = render(art, frame(art, fx = mapOf(name to v), time = 3.0 + v))
                fxs += c
                write(c, File(d, "fx-$name-${(v * 10).toInt()}.png"), 3)
            }
            // the pokes
            for (pk in ScenePainters.create(art).pokes) for (step in pk.steps.indices) for (age in listOf(0.4, 1.0)) {
                write(render(art, frame(art, time = 3.0, pokes = mapOf(pk.id to Poked(step, 3.0 - age)))), File(d, "poke-${pk.id}-$step-${(age * 10).toInt()}.png"), 3)
            }
            val extra = ArrayList<PixelCanvas>()
            if (art == "palisade") for (lv in 1..5) {
                val c = render(art, frame(art, world = palisadeAt(lv)))
                extra += c
                write(c, File(d, "level-$lv.png"), 4)
                val n = render(art, frame(art, hour = 21.5f, month = 10, world = palisadeAt(lv)))
                extra += n
                write(n, File(d, "level-$lv-night.png"), 4)
            }
            sheet(main + extra, 4, 2, File(dir, "$art-sheet.png"))
            sheet(fxs, 4, 1, File(dir, "$art-effects.png"))
        }
    }

    @Test fun `every effect changes the picture`() {
        val same = ArrayList<String>()
        for (art in arts) for (name in SceneArt.effects.getValue(art)) {
            // an effect that goes and comes back (a traveller who is gone at 1) shows on its way: 0, halfway, 1
            val looks = listOf(0f, 0.5f, 1f).map { render(art, frame(art, fx = mapOf(name to it), hour = 21.5f, month = 7)).pixels.toList() }.toSet()
            if (looks.size < 2) same += "$art/$name"
        }
        assertEquals("effects that change nothing", emptyList<String>(), same)
    }

    @Test fun `Friuli's look changes each place and keeps every thing there to tap`() {
        for (art in arts) {
            val friuli = SceneWorld(culture = "friuli")
            val a = PixelCanvas(240, 160); val b = PixelCanvas(240, 160)
            val pa = ScenePainters.create(art); val pb = ScenePainters.create(art)
            val ha = pa.render(a, frame(art)).map { it.target }.toSet()
            val hb = pb.render(b, frame(art, world = friuli)).map { it.target }.toSet()
            assertNotEquals("$art: Friuli looks like Primorska", a.pixels.toList(), b.pixels.toList())
            assertEquals("$art: the same things and people to tap in Friuli", ha, hb)
            for (slot in SceneArt.objects.getValue(art)) assertTrue("$art/$slot tappable in Friuli", SceneTarget.Thing(slot) in hb)
        }
    }

    @Test fun `the palisade's gate looks different at every level`() {
        if ("palisade" !in arts) return
        val looks = (1..5).map { render("palisade", frame("palisade", world = palisadeAt(it), who = false)).pixels.toList() }
        for (lv in 2..5) assertNotEquals("level $lv looks like level ${lv - 1}", looks[lv - 2], looks[lv - 1])
        assertEquals("the top level is the default picture", render("palisade", frame("palisade", who = false)).pixels.toList(), looks[4])
    }
}
