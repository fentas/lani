package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFixtures
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.Strike
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The places come alive (docs/plans/05-built/place-life.md, companion/SCENES.md "What the village has built"): the finished
 * projects are things of the pictures they stand in (the square's maypole, fountain and clock, the stream's cart bridge,
 * the field's toplar, the apiary's bee meadow), in every culture's look, each there to tap once its project is finished and
 * scenery before; the square's standing spots by them; Friuli's greasy pole with its wheel of prizes and a lad climbing it;
 * the clock striking the hour. Contact sheets for review in build/scene-snapshots/place-life: each art at each stage of its
 * project, each culture, each detail and the times of the day.
 */
class PlaceLifeRenderTest {
    private val dir = File("build/scene-snapshots/place-life").apply { mkdirs() }

    /** Each new thing: its art, its slot, its project. */
    private val things = listOf(
        Triple("square", "maypole", "mlaj"), Triple("square", "fountain", "vodnjak_na_trgu"), Triple("square", "clock", "mestna_ura"),
        Triple("stream", "cartbridge", "most"), Triple("field", "toplar", "toplar"), Triple("apiary", "meadow", "cebelji_travnik"),
    )
    private val cultures = listOf("primorska", "friuli")

    private fun stages(project: String) = Catalog.projectFrames.first { it.id == project }.steps.size

    /** Everything built, [project] at [steps] steps; [culture]'s look. */
    private fun world(project: String? = null, steps: Int = 0, culture: String = "primorska", level: Int? = null, art: String? = null): SceneWorld {
        var w = SceneWorld.ALL.copy(culture = culture)
        if (project != null) w = w.stepping(project, steps)
        if (level != null && art != null) {
            val type = mapOf("apiary" to BuildingType.BEEHIVE, "field" to BuildingType.FIELD).getValue(art)
            w = w.copy(here = Building("here", type, 0, level = level))
        }
        return w
    }

    private fun frame(
        art: String, world: SceneWorld, hour: Float = 11f, month: Int = 6, time: Double = 3.0, fx: Map<String, Float> = emptyMap(),
        people: List<PersonInScene> = emptyList(), count: Int? = null,
    ) = SceneFrame(time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), people = people, fx = fx, world = world, count = count)

    private class Shot(val label: String, val canvas: PixelCanvas, val hits: List<SceneHit>)

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160, label: String = ""): Shot {
        val c = PixelCanvas(w * k, h * k)
        val hits = ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return Shot(label, c, hits)
    }

    private fun Shot.has(slot: String) = hits.any { it.target == SceneTarget.Thing(slot) }

    /** A labelled sheet of shots, [cols] across, each at [scale] (shots of other sizes scaled to the first's cell). */
    private fun sheet(shots: List<Shot>, cols: Int, scale: Int, f: File) {
        if (shots.isEmpty()) return
        val cw = 240 * scale; val chh = 160 * scale; val lab = 16
        val rows = (shots.size + cols - 1) / cols
        val img = BufferedImage(cw * cols, (chh + lab) * rows, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(30, 30, 34); g.fillRect(0, 0, img.width, img.height)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        for ((i, s) in shots.withIndex()) {
            val ox = (i % cols) * cw; val oy = (i / cols) * (chh + lab)
            val c = s.canvas
            for (y in 0 until chh) for (x in 0 until cw) {
                val sx = x * c.width / cw; val sy = y * c.height / chh
                img.setRGB(ox + x, oy + lab + y, c.pixels[sy * c.width + sx])
            }
            g.color = Color(230, 230, 230); g.drawString(s.label, ox + 4, oy + 12)
        }
        g.dispose()
        f.parentFile.mkdirs()
        ImageIO.write(img, "png", f)
        // each shot on its own too, 960 px wide, to look at closely
        for ((i, s) in shots.withIndex()) {
            val c = s.canvas
            val sc = maxOf(1, 960 / c.width)
            val one = BufferedImage(c.width * sc, c.height * sc, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until one.height) for (x in 0 until one.width) one.setRGB(x, y, c.pixels[(y / sc) * c.width + x / sc])
            val name = s.label.replace(Regex("[^A-Za-z0-9.]+"), "-")
            ImageIO.write(one, "png", File(f.parentFile, "shots/${f.nameWithoutExtension}-$i-$name.png").apply { parentFile.mkdirs() })
        }
    }

    private val sq = "square"

    /** The square's people: someone at every standing spot, the children at play, someone on the bench and at the well. */
    private fun squarePeople(standing: Boolean = true) = listOfNotNull(
        PersonInScene("b", "grandpa", "bench"), PersonInScene("w", "smith", "well"),
        PersonInScene("c1", "child1", "play1"), PersonInScene("c3", "child3", "play3"),
        PersonInScene("m", "shepherd", "maypole").takeIf { standing }, PersonInScene("f", "farmer", "fountain").takeIf { standing },
        PersonInScene("k", "teacher", "clock").takeIf { standing },
    )

    @Test fun `render the places' new things for review`() {
        // each thing: its project's stages one by one, in both cultures, by day; then finished at each detail and hour
        for ((art, slot, project) in things) {
            val shots = ArrayList<Shot>()
            for (culture in cultures) for (st in 0..stages(project)) {
                val f = frame(art, world(project, st, culture), month = if (art == "apiary") 7 else 6)
                shots += render(art, f, label = "$culture $project $st/${stages(project)}")
            }
            sheet(shots, 4, 2, File(dir, "$art-$slot-stages.png"))
            val done = ArrayList<Shot>()
            for (culture in cultures) for ((name, hour, month) in listOf(Triple("day", 11f, 7), Triple("evening", 19.6f, 9), Triple("night", 22.5f, 10), Triple("winter", 11f, 1))) {
                val f = frame(art, world(culture = culture), hour = hour, month = month, people = if (art == sq) squarePeople() else emptyList())
                done += render(art, f, label = "$culture $name")
                for (k in 2..Lens.MAX_DETAIL) if (name == "day" || name == "night") done += render(art, f, k = k, label = "$culture $name detail $k")
            }
            sheet(done, 4, 2, File(dir, "$art-$slot-finished.png"))
        }
    }

    @Test fun `render the square's spots, the greasy pole, the climb and the strike for review`() {
        val shots = ArrayList<Shot>()
        for (culture in cultures) {
            val w = world(culture = culture)
            shots += render(sq, frame(sq, w, people = squarePeople()), label = "$culture everyone")
            shots += render(sq, frame(sq, w, people = squarePeople(), hour = 20.5f, month = 5), label = "$culture evening")
            shots += render(sq, frame(sq, w, people = listOf(PersonInScene("a", "aunt", "play2"), PersonInScene("c", "child2", "play1"), PersonInScene("d", "child1", "play3"))), label = "$culture grown-up at play2")
            for (v in listOf(0f, 0.3f, 0.6f, 1f)) shots += render(sq, frame(sq, w, month = 5, fx = mapOf("climb" to v)), k = 2, label = "$culture climb $v")
            for (lv in listOf(0.03f, 0.1f, 0.2f, 0.5f, 0.9f)) shots += render(sq, frame(sq, w, month = 5, hour = 15.2f, fx = mapOf("strike" to lv), count = 3), k = 2, label = "$culture strike 3 at $lv")
            // the greasy pole's steps closer up
            for (st in 2..stages("mlaj")) shots += render(sq, frame(sq, world("mlaj", st, culture), month = 5), k = 2, label = "$culture mlaj $st")
            shots += render(sq, frame(sq, world("mlaj", stages("mlaj") - 1, culture), month = 5, fx = mapOf("maypole" to 0.5f)), k = 2, label = "$culture raising")
        }
        sheet(shots, 4, 2, File(dir, "square-life.png"))
        // the closest look at the pole's top and the fountain
        val close = ArrayList<Shot>()
        for (culture in cultures) {
            close += render(sq, frame(sq, world(culture = culture), month = 5, fx = mapOf("climb" to 1f)), k = 3, w = 240, h = 160, label = "$culture top")
        }
        sheet(close, 2, 3, File(dir, "square-close.png"))
    }

    @Test fun `render the bee meadow through the year and the bee garden's levels`() {
        val shots = ArrayList<Shot>()
        for (culture in cultures) for (m in listOf(4, 5, 6, 7, 8, 9, 10, 12)) shots += render("apiary", frame("apiary", world(culture = culture), month = m), label = "$culture month $m")
        for (culture in cultures) for (lv in 1..5) shots += render("apiary", frame("apiary", world(culture = culture, level = lv, art = "apiary"), month = 7), label = "$culture level $lv")
        sheet(shots, 4, 2, File(dir, "apiary-meadow-year.png"))
        val field = ArrayList<Shot>()
        for (m in listOf(6, 8, 11, 2)) field += render("field", frame("field", world(culture = "friuli"), month = m), k = 2, label = "friuli month $m")
        sheet(field, 2, 2, File(dir, "field-maize.png"))
    }

    // ------------------------------------------------------------------ what they promise

    @Test fun `each new thing is there to tap once its project is finished, in every culture, and scenery before`() {
        val fails = ArrayList<String>()
        for ((art, slot, project) in things) for (culture in cultures) {
            val n = stages(project)
            for (st in 0..n) {
                val w = world(project, st, culture)
                val s = render(art, frame(art, w))
                val expected = SceneFixtures.there(art, slot, w)
                if (s.has(slot) != expected) fails += "$culture $art/$slot at $st/$n: tappable ${s.has(slot)}, there $expected"
            }
            for (k in 1..Lens.MAX_DETAIL) if (!render(art, frame(art, world(culture = culture)), k = k).has(slot)) fails += "$culture $art/$slot not tappable at detail $k"
        }
        assertEquals(emptyList<String>(), fails)
    }

    @Test fun `with the projects not finished, the default culture's pictures are as they were`() {
        // the things drawn before, scenery then: a prop and a thing draw the same pixels, so only the bee meadow is new
        for ((art, slot, project) in things) {
            if (slot == "meadow") continue
            val before = world(project, stages(project) - 1)
            val a = render(art, frame(art, world()))
            // the same picture with the slot asked for and not: the thing draws what the scenery drew
            val b = render(art, frame(art, world()).copy(objects = SceneArt.objects.getValue(art).toSet() - slot))
            assertTrue("$art/$slot: the same pixels as a thing and as scenery", a.canvas.pixels.contentEquals(b.canvas.pixels))
            assertFalse("$art/$slot: not there before", render(art, frame(art, before)).has(slot))
        }
        // without its project the apiary draws no meadow
        val none = render("apiary", frame("apiary", world("cebelji_travnik", 0)))
        val all = render("apiary", frame("apiary", world()))
        assertFalse(none.has("meadow"))
        assertNotEquals(none.canvas.pixels.toList(), all.canvas.pixels.toList())
    }

    @Test fun `Friuli's look draws each new thing its own way`() {
        for ((art, slot) in listOf("square" to "maypole", "square" to "fountain", "stream" to "cartbridge", "field" to "toplar", "apiary" to "meadow")) {
            val month = if (slot == "toplar") 9 else 7
            val p = render(art, frame(art, world(), month = month)).hits.first { it.target == SceneTarget.Thing(slot) }
            val a = render(art, frame(art, world(), month = month)).canvas
            val b = render(art, frame(art, world(culture = "friuli"), month = month)).canvas
            var diff = 0
            for (y in p.top until p.bottom) for (x in p.left until p.right) if (a.pixels[y * a.width + x] != b.pixels[y * b.width + x]) diff++
            assertTrue("$art/$slot: Friuli's differs in its box ($diff px)", diff >= 6)
        }
    }

    @Test fun `the climb takes a lad up the pole and the strike rings each stroke`() {
        for (culture in cultures) {
            val w = world(culture = culture)
            val still = render(sq, frame(sq, w, month = 5)).canvas.pixels.toList()
            val looks = listOf(0f, 0.5f, 1f).map { render(sq, frame(sq, w, month = 5, fx = mapOf("climb" to it))).canvas.pixels.toList() }
            assertEquals("$culture: three heights", 3, looks.toSet().size)
            assertTrue("$culture: someone on the pole", looks.none { it == still })
        }
        // the strokes: one ringing while the level is in its time, none before the first or after the last
        assertEquals(null, Strike.now(0f, 3))
        assertEquals(0, Strike.now(Strike.at(0) + 0.001f, 3)?.first)
        assertEquals(2, Strike.now(Strike.at(2) + 0.001f, 3)?.first)
        assertEquals(null, Strike.now(Strike.at(3) + 0.001f, 3))
        assertEquals(12, (0..2400).map { Strike.now(it / 2400f, 12)?.first }.filterNotNull().toSet().size)
        assertEquals(listOf(12, 1, 1, 12, 3), listOf(0f, 1f, 13.5f, 12f, 15.9f).map { Strike.hour12(it) })
        // the bell swings for a stroke, and not between them
        val between = render(sq, frame(sq, world(), fx = mapOf("strike" to 0f), count = 2)).canvas.pixels.toList()
        val ringing = render(sq, frame(sq, world(), fx = mapOf("strike" to Strike.at(1) + 0.02f), count = 2)).canvas.pixels.toList()
        val after = render(sq, frame(sq, world(), fx = mapOf("strike" to 1f), count = 2)).canvas.pixels.toList()
        assertNotEquals(between, ringing)
        assertEquals("all rung, as before", between, after)
    }

    @Test fun `a grown-up at a play spot stands and watches, and the standing spots are clear of each other`() {
        val w = world()
        val grown = frame(sq, w, people = listOf(PersonInScene("a", "aunt", "play2")))
        val a = render(sq, grown.copy(time = 3.0)).canvas.pixels.toList()
        val b = render(sq, grown.copy(time = 4.1)).canvas.pixels.toList()
        val child = frame(sq, w, people = listOf(PersonInScene("a", "child2", "play2"), PersonInScene("b", "child1", "play1")))
        val c = render(sq, child.copy(time = 3.0)).canvas.pixels.toList()
        val d = render(sq, child.copy(time = 4.1)).canvas.pixels.toList()
        // she stays where she is (the pigeons and the leaves move on their own: compare her box)
        val hit = render(sq, grown).hits.first { it.target == SceneTarget.Person("a") }
        val hit2 = render(sq, grown.copy(time = 4.1)).hits.first { it.target == SceneTarget.Person("a") }
        assertEquals("a grown-up at play2 stands still", hit.left to hit.top, hit2.left to hit2.top)
        assertNotEquals("the children play", c, d)
        assertTrue(a.isNotEmpty() && b.isNotEmpty())
        val boxes = squarePeople().associate { p -> p.slot to render(sq, frame(sq, w, people = squarePeople())).hits.first { it.target == SceneTarget.Person(p.id) } }
        for (x in listOf("maypole", "fountain", "clock")) for (y in listOf("maypole", "fountain", "clock")) if (x < y) {
            val p = boxes.getValue(x); val q = boxes.getValue(y)
            val overlap = minOf(p.right, q.right) - maxOf(p.left, q.left)
            assertTrue("$x and $y overlap by $overlap px", overlap <= 2)
        }
    }
}
