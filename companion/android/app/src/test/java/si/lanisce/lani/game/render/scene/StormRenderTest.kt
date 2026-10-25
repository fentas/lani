package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.render.CameraRig
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Frame
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.SceneFit
import si.lanisce.lani.game.render.VillageLayout
import si.lanisce.lani.game.render.VillageRenderer
import si.lanisce.lani.game.scene.Lightning
import si.lanisce.lani.game.scene.PersonInScene
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
 * The storm drawn (companion/GAME.md "People react", SCENES.md "The rain indoors"): over the village the rain streaks down
 * and rings out on the wet ground; a room's window runs with it; a flash of lightning is one soft pulse now and then, none
 * with the system's animations off; a frame with the rain stays inside its budget. Snapshots for review in
 * build/scene-snapshots/storm.
 */
class StormRenderTest {
    private val dir = File("build/scene-snapshots/storm").apply { mkdirs() }
    private val bw = 270; private val bh = 297

    private val town: GameState = run {
        val types = listOf(BuildingType.HUT, BuildingType.FIELD, BuildingType.WELL, BuildingType.TENT, BuildingType.KOZOLEC, BuildingType.HOUSE, BuildingType.LIPA,
            BuildingType.BEEHIVE, BuildingType.HOUSE, BuildingType.SMITHY, BuildingType.MARKET, BuildingType.HOUSE, BuildingType.SCHOOL, BuildingType.WATCHTOWER,
            BuildingType.HOUSE, BuildingType.CHURCH)
        GameState(
            seed = 42, age = Age.VAS, villagers = 6, morale = 80, fire = 80, land = Land.CLASSIC,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i, level = 2, builtAt = i.toLong()) } + Building("pal", BuildingType.PALISADE, NO_PLOT),
        )
    }
    private val storm = town.copy(event = GameEvent("ev", EventKind.STORM, 2, 0L, 0L))
    private val fit = SceneFit.town(1080, 2400)

    /** At home in the storm: Babica Micka by the stove, Teta Ančka at the table, Zala by the door. */
    private val kitchen = listOf(PersonInScene("babica", "grandma", "stove"), PersonInScene("ancka", "aunt", "table"), PersonInScene("zala", "child2", "door"))
    private val livingRoom = listOf(PersonInScene("janez", "grandpa", "table"), PersonInScene("joze", "hunter", "stove"))

    private fun frame(art: String, sky: Sky, time: Double = 3.0, hour: Float = 14f, people: List<PersonInScene> = emptyList(), world: SceneWorld = SceneWorld.ALL) =
        SceneFrame(time = time, hour = hour, month = 10, objects = SceneArt.objects.getValue(art).toSet(), people = people, sky = sky, world = world)

    private fun render(art: String, f: SceneFrame, k: Int = 1): PixelCanvas {
        val c = PixelCanvas(bw * k, bh * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, bw, bh))
        return c
    }

    private fun village(s: GameState, time: Double, hour: Float = 11f, k: Int = 1, calm: Boolean = false): PixelCanvas {
        val c = PixelCanvas(fit.width, fit.height)
        val comp = VillageLayout.composition(fit.width, fit.height, compact = false)
        val rig = CameraRig.town(fit, 1080, 2400)
        val cam = rig.lookAt(comp.fireX.toFloat(), comp.fireY.toFloat() + 10f, (rig.minScale * k).toFloat())
        val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
        VillageRenderer().render(c, s, Frame(time = time, hour = hour, month = 10, lens = lens, calm = calm, today = "2026-10-13"))
        return c
    }

    private fun luma(c: PixelCanvas): Double = c.pixels.sumOf { (Col.r(it) * 0.3 + Col.g(it) * 0.59 + Col.b(it) * 0.11) } / c.pixels.size

    @Test fun `a flash is one soft pulse, now and then, never quick`() {
        assertEquals(0f, Lightning.glow(-0.01), 0f)
        assertEquals(1f, Lightning.glow(Lightning.RISE_S), 1e-4f)
        assertEquals(0f, Lightning.glow(Lightning.SHOWS_S + 0.01), 0f)
        // it rises, then only fades: one pulse, no flicker, no echo
        var last = 0f; var falling = false
        var t = 0.0
        while (t < Lightning.SHOWS_S) {
            val g = Lightning.glow(t)
            if (g < last) falling = true
            if (falling) assertTrue("rises again at $t", g <= last + 1e-6f)
            last = g; t += 0.005
        }
        // at least 5 s from one flash to the next, in the storm, a scene's and a kept sky's: well under 3 a second
        for (l in listOf(Lightning.STORM, Lightning.SCENE, Lightning.KEPT)) for (n in 0 until 300) assertTrue("${l.period}: $n", l.at(n + 1) - l.at(n) >= 5.0)
        // and not in a rhythm: the storm's gaps vary
        val gaps = (0 until 40).map { Lightning.STORM.at(it + 1) - Lightning.STORM.at(it) }
        assertTrue("$gaps", gaps.max() - gaps.min() > 3.0)
    }

    @Test fun `over the village in the storm the rain falls, more of the sky moves than on a dry day, and a flash is soft`() {
        val dry = village(town, 3.0); val dryLater = village(town, 3.05)
        val wet = village(storm, 3.0); val wetLater = village(storm, 3.05)
        val sky = VillageLayout.composition(fit.width, fit.height, compact = false).horizon
        fun moved(a: PixelCanvas, b: PixelCanvas, y0: Int, y1: Int) = (y0 until y1).sumOf { y -> (0 until a.width).count { x -> a.pixels[y * a.width + x] != b.pixels[y * a.width + x] } }
        val dryMoved = moved(dry, dryLater, 0, fit.height); val wetMoved = moved(wet, wetLater, 0, fit.height)
        assertTrue("rain moving: $wetMoved px against $dryMoved", wetMoved > dryMoved + 3000)
        assertTrue("in the sky too: ${moved(wet, wetLater, 0, sky)}", moved(wet, wetLater, 0, sky) > 300)
        // the storm's light is darker than a dry day's
        assertTrue(luma(wet) < luma(dry))
        // a flash brightens the village a little, softly; with the system's animations off, not at all
        val at = Lightning.STORM.at(4) + Lightning.RISE_S
        val flash = village(storm, at); val calm = village(storm, at, calm = true); val between = village(storm, at - 2.0)
        assertTrue("a flash: ${luma(flash)} vs ${luma(between)}", luma(flash) > luma(between) + 3)
        assertTrue("soft: ${luma(flash) - luma(between)}", luma(flash) - luma(between) < 50)
        assertTrue("calm: ${luma(calm)} vs ${luma(between)}", luma(calm) < luma(between) + 2)
    }

    @Test fun `a room's window runs with the rain, greys under the storm and flashes with it`() {
        for ((art, who) in listOf("kitchen" to kitchen, "livingroom" to livingRoom, "workshop" to emptyList(), "attic" to emptyList(), "cellar" to emptyList(), "school" to emptyList(), "smithy" to emptyList())) {
            val painter = ScenePainters.create(art)
            val c = PixelCanvas(bw, bh)
            val hits = painter.render(c, frame(art, Sky.CLEAR, people = who))
            val win = hits.firstOrNull { it.target == SceneTarget.Thing("window") } ?: continue
            fun window(cv: PixelCanvas) = (win.top until win.bottom).flatMap { y -> (win.left until win.right).map { x -> cv.pixels[y * cv.width + x] } }
            val clear = window(c)
            val wet = window(render(art, frame(art, Sky.STORM.copy(lightning = false), people = who)))
            val wetLater = window(render(art, frame(art, Sky.STORM.copy(lightning = false), time = 4.1, people = who)))
            val changed = clear.indices.count { clear[it] != wet[it] }
            assertTrue("$art: the storm on the window ($changed of ${clear.size} px)", changed > clear.size / 6)
            val running = wet.indices.count { wet[it] != wetLater[it] }
            // (the cellar's slit of a window, high in the wall, is too small to show much of it)
            if (art != "cellar") assertTrue("$art: the rain runs on the glass ($running px of ${clear.size})", running > 3)
        }
    }

    @Test fun `a cold hearth shows a dark stove, a lit one glows`() {
        val lit = render("kitchen", frame("kitchen", Sky.STORM.copy(lightning = false), hour = 20f, people = kitchen))
        val cold = render("kitchen", frame("kitchen", Sky.STORM.copy(lightning = false), hour = 20f, people = kitchen, world = SceneWorld.ALL.copy(cold = true)))
        assertTrue("the cold kitchen is darker: ${luma(cold)} vs ${luma(lit)}", luma(cold) < luma(lit) - 0.5)
    }

    @Test fun `the storm renders inside the frame budget`() {
        // the village on a portrait phone, the fitted canvas and closer up at detail 3
        for (k in listOf(1, 3)) {
            repeat(8) { village(storm, it / 12.0, k = k) }
            val n = 30
            val c = PixelCanvas(fit.width, fit.height)
            val comp = VillageLayout.composition(fit.width, fit.height, compact = false)
            val rig = CameraRig.town(fit, 1080, 2400)
            val lens = Lens.of(k, rig.lookAt(comp.fireX.toFloat(), comp.fireY.toFloat(), (rig.minScale * k).toFloat()), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val r = VillageRenderer()
            val t0 = System.nanoTime()
            repeat(n) { r.render(c, storm, Frame(time = 2 + it / 12.0, hour = 11f, month = 10, lens = lens, today = "2026-10-13")) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("VillageRenderer in the storm: %.2f ms/frame (detail %d, %dx%d)".format(ms, k, fit.width, fit.height))
            assertTrue("too slow: $ms ms", ms < FrameBudget.scaled(50.0))
        }
        // the kitchen in the storm, its people at home, at the scene's size and closer up
        val p = ScenePainters.create("kitchen")
        val c = PixelCanvas(360, 240)
        val f = frame("kitchen", Sky.STORM, people = kitchen)
        repeat(15) { p.render(c, f.copy(time = it / 12.0)) }
        val t0 = System.nanoTime()
        repeat(40) { p.render(c, f.copy(time = 2 + it / 12.0)) }
        val ms = (System.nanoTime() - t0) / 1e6 / 40
        println("ScenePainter kitchen in the storm: %.2f ms/frame (360x240)".format(ms))
        assertTrue("too slow: $ms ms", ms < FrameBudget.scaled(20.0))
        val lens = Lens(3, (360 * 3 - 360) / 2, (240 * 3 - 240) / 2, 360, 240)
        repeat(5) { p.render(c, f.copy(time = it / 12.0), lens) }
        val t1 = System.nanoTime()
        repeat(20) { p.render(c, f.copy(time = 2 + it / 12.0), lens) }
        val msFine = (System.nanoTime() - t1) / 1e6 / 20
        println("ScenePainter kitchen in the storm: %.2f ms/frame (detail 3)".format(msFine))
        assertTrue("too slow closer up: $msFine ms", msFine < FrameBudget.scaled(60.0))
    }

    @Test fun `snapshots of the storm for review`() {
        val flash = Lightning.STORM.at(4) + Lightning.RISE_S
        for (k in listOf(1, 3)) {
            write(village(storm, 3.0, k = k), File(dir, "village-storm-k$k.png"), 2)
            write(village(storm, flash, k = k), File(dir, "village-storm-flash-k$k.png"), 2)
        }
        write(village(storm, 3.0, hour = 20.5f), File(dir, "village-storm-evening.png"), 2)
        val scene = Lightning.SCENE.at(2) + Lightning.RISE_S
        write(render("kitchen", frame("kitchen", Sky.STORM, people = kitchen)), File(dir, "kitchen-storm.png"), 4)
        write(render("kitchen", frame("kitchen", Sky.STORM, time = scene, people = kitchen)), File(dir, "kitchen-storm-flash.png"), 4)
        write(render("kitchen", frame("kitchen", Sky.STORM, hour = 20f, people = kitchen)), File(dir, "kitchen-storm-evening.png"), 4)
        write(render("kitchen", frame("kitchen", Sky.STORM, hour = 20f, people = kitchen, world = SceneWorld.ALL.copy(cold = true))), File(dir, "kitchen-storm-cold.png"), 4)
        write(render("kitchen", frame("kitchen", Sky.STORM, people = kitchen), 3), File(dir, "kitchen-storm-k3.png"), 2)
        write(render("livingroom", frame("livingroom", Sky.STORM, people = livingRoom)), File(dir, "livingroom-storm.png"), 4)
        for (art in listOf("forest", "field", "square", "campfire")) write(render(art, frame(art, Sky.STORM)), File(dir, "$art-storm.png"), 4)
        // the wolves: the children and the old by the fire, beside the stump and in front of the woodpile too
        val gathered = listOf(
            PersonInScene("babica", "grandma", "left"), PersonInScene("janez", "grandpa", "right"), PersonInScene("otroci", "child1", "back"),
            PersonInScene("zala", "child2", "behind-stump"), PersonInScene("ancka", "aunt", "behind-logs"),
        )
        write(render("campfire", frame("campfire", Sky.CLEAR, hour = 10.5f, people = gathered)), File(dir, "campfire-wolves.png"), 4)
    }

    private fun write(c: PixelCanvas, f: File, s: Int) {
        val img = BufferedImage(c.width * s, c.height * s, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, c.pixels[(y / s) * c.width + x / s])
        ImageIO.write(img, "png", f)
    }
}
