package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poked
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.Sleep
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The houses' rooms asleep at night (companion/SCENES.md, "Asleep at night"): Micka on the kitchen's bench, Ančka on a straw
 * mattress by the door, Zala on a small one in the corner; Stari Janez on the zapeček with Muri at his feet, Lovec Jože on
 * its other run; Vida on a straw mattress beside Nejc's bed, Nejc in it; France on a straw mattress in the workshop, Tine on
 * his cot; Vinar Marko on his bench by the barrels, Čebelar Anton on his trestle bed by the steps; and the workplaces with a
 * bed: Kovač Tone on his cot by the forge, Učiteljica Mojca in her curtained bed in the school. Each room declares its beds
 * (the children's among them), and at every level and detail each sleeper lies in their bed, drawn and tappable there,
 * turns over and grumbles when tapped, and the room goes quiet for them (the smithy's forge banked). Snapshots for review in
 * build/scene-snapshots/sleep.
 */
class HomeBedsRenderTest {
    private val dir = File("build/scene-snapshots/sleep").apply { mkdirs() }
    private val bw = 270; private val bh = 297

    /** A room with its building and who sleeps in which of its beds. */
    private class Home(val art: String, val type: BuildingType, val beds: List<String>, val children: Set<String>, val sleepers: List<PersonInScene>)

    private fun asleep(id: String, art: String, bed: String) = PersonInScene(id, art, bed, pose = Pose.SLEEP)

    private val homes = listOf(
        Home("kitchen", BuildingType.HUT, listOf("zapecek", "mattress", "cot"), setOf("cot"),
            listOf(asleep("micka", "grandma", "zapecek"), asleep("ancka", "aunt", "mattress"), asleep("zala", "child2", "cot"))),
        Home("livingroom", BuildingType.HOUSE, listOf("zapecek", "sidebench"), emptySet(),
            listOf(asleep("janez", "grandpa", "zapecek"), asleep("joze", "hunter", "sidebench"))),
        Home("attic", BuildingType.HOUSE, listOf("mattress", "cot"), setOf("cot"),
            listOf(asleep("vida", "innkeeper", "mattress"), asleep("nejc", "child1", "cot"))),
        Home("workshop", BuildingType.HOUSE, listOf("mattress", "cot"), setOf("cot"),
            listOf(asleep("france", "farmer", "mattress"), asleep("tine", "child3", "cot"))),
        Home("cellar", BuildingType.HOUSE, listOf("bench", "trestle"), emptySet(),
            listOf(asleep("marko", "winemaker", "bench"), asleep("anton", "beekeeper", "trestle"))),
        Home("smithy", BuildingType.SMITHY, listOf("cot"), emptySet(), listOf(asleep("tone", "smith", "cot"))),
        Home("school", BuildingType.SCHOOL, listOf("alcove"), emptySet(), listOf(asleep("mojca", "teacher", "alcove"))),
    )

    private fun frame(h: Home, level: Int, people: List<PersonInScene> = h.sleepers, hour: Float = 23.5f, time: Double = 3.0, pokes: Map<String, Poked> = emptyMap(), month: Int = 7) =
        SceneFrame(time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(h.art).toSet(), world = SceneWorld.room(level, h.type), people = people, pokes = pokes)

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = bw, h: Int = bh): Triple<PixelCanvas, List<SceneHit>, si.lanisce.lani.game.scene.ScenePainter> {
        val c = PixelCanvas(w * k, h * k)
        val p = ScenePainters.create(art)
        return Triple(c, p.render(c, f, Lens(k, 0, 0, w, h)), p)
    }

    private fun changed(a: PixelCanvas, b: PixelCanvas, r: SceneHit? = null, k: Int = 1): Int {
        if (r == null) return a.pixels.indices.count { a.pixels[it] != b.pixels[it] }
        var n = 0
        for (y in r.top * k until r.bottom * k) for (x in r.left * k until r.right * k) if (a.pixels[y * a.width + x] != b.pixels[y * b.width + x]) n++
        return n
    }

    @Test fun `every room declares its beds, the children's among them`() {
        for (h in homes) {
            val p = ScenePainters.of(h.art)
            assertEquals("${h.art}: its beds, in order", h.beds, p.beds)
            assertEquals("${h.art}: the children's beds", h.children, p.childBeds)
            assertTrue("${h.art}: a child's bed is one of its beds", p.beds.containsAll(p.childBeds))
            assertEquals("${h.art}: Sleep knows them", h.beds, Sleep.beds(h.art))
        }
        // and every other painter's children's beds are among its beds
        for (art in SceneArt.arts) ScenePainters.of(art).let { assertTrue("$art: childBeds ⊂ beds", it.beds.containsAll(it.childBeds)) }
    }

    @Test fun `every sleeper lies in their bed at every level and detail, drawn and tappable there`() {
        val failed = ArrayList<String>()
        for (h in homes) for (level in 1..3) for (k in 1..Lens.MAX_DETAIL) {
            val (c, hits, painter) = render(h.art, frame(h, level), k)
            for (s in h.sleepers) {
                val hit = hits.singleOrNull { it.target == SceneTarget.Person(s.id) }
                if (hit == null) { failed += "${h.art} L$level d$k: ${s.id} not drawn"; continue }
                val ww = hit.right - hit.left; val hh = hit.bottom - hit.top
                // lying along the bed: wider than tall, a grown-up longer than a child
                if (ww !in 16..44 || hh !in 10..34) failed += "${h.art} L$level d$k: ${s.id} not lying: $hit"
                // tappable on their own pixels
                var found = 0
                for (y in hit.top * k until hit.bottom * k) for (x in hit.left * k until hit.right * k) if (painter.targetAt(c, x, y, 0) == hit.target) found++
                if (found < 60 * k * k) failed += "${h.art} L$level d$k: ${s.id} tappable on only $found px"
                // drawn: the room without them differs there
                val (without, _) = render(h.art, frame(h, level, h.sleepers - s), k)
                val diff = changed(c, without, hit, k)
                if (diff < 80 * k * k) failed += "${h.art} L$level d$k: ${s.id} barely drawn ($diff px)"
            }
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `the sleepers and their beds hide none of the room's things`() {
        // every thing of the room at night is still there to tap with everyone asleep, at every level and detail
        val failed = ArrayList<String>()
        fun shown(c: PixelCanvas, painter: si.lanisce.lani.game.scene.ScenePainter, hit: SceneHit, k: Int): Int {
            var n = 0
            for (y in hit.top * k until hit.bottom * k) for (x in hit.left * k until hit.right * k) if (painter.targetAt(c, x, y, 0) == hit.target) n++
            return n
        }
        for (h in homes) for (level in 1..3) for (k in 1..Lens.MAX_DETAIL) {
            val (ec, emptyHits, ep) = render(h.art, frame(h, level, people = emptyList()), k)
            val before = emptyHits.filter { it.target is SceneTarget.Thing }.associate { it.target to shown(ec, ep, it, k) }
            val (c, hits, painter) = render(h.art, frame(h, level), k)
            val things = hits.map { it.target }.filterIsInstance<SceneTarget.Thing>().toSet()
            if (things != before.keys) failed += "${h.art} L$level d$k: ${before.keys - things} hidden, ${things - before.keys} new"
            // and each still shows at least half of what it showed without them, but for the bedding they lie in (the
            // kitchen's bench and straw bed, the zapeček, Nejc's bed, pillow and quilt) and the smithy's fire, banked for
            // the night (no flames)
            val bedding = setOf("bench", "bed", "pillow", "blanket").map { SceneTarget.Thing(it) } +
                (if (h.art == "smithy") listOf(SceneTarget.Thing("fire")) else emptyList())
            for (hit in hits) if (hit.target is SceneTarget.Thing && hit.target !in bedding) {
                val n = shown(c, painter, hit, k); val was = before[hit.target] ?: continue
                if (n * 2 < was) failed += "${h.art} L$level d$k: ${hit.target} shows $n px of $was"
            }
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `a tap turns each sleeper over and another has them grumble, and they stay there to tap`() {
        val failed = ArrayList<String>()
        for (h in homes) for (level in listOf(1, 3)) for (s in h.sleepers) for ((step, d) in listOf(Sleep.TURN_S, Sleep.MUMBLE_S).withIndex()) for (k in 1..Lens.MAX_DETAIL) {
            val rest = render(h.art, frame(h, level), k)
            val diff = (0 until 4).sumOf { i ->
                val age = d * (i + 0.5) / 4
                changed(rest.first, render(h.art, frame(h, level, pokes = mapOf(Sleep.pokeId(s.id) to Poked(step, 3.0 - age))), k).first)
            }
            if (diff < 50 * k * k) failed += "${h.art} L$level ${s.id} ${if (step == 0) "turning over" else "grumbling"} at detail $k: $diff px change"
            val targets = render(h.art, frame(h, level, pokes = mapOf(Sleep.pokeId(s.id) to Poked(step, 3.0 - 1.0))), k).second.map { it.target }.toSet()
            if (targets != rest.second.map { it.target }.toSet()) failed += "${h.art} L$level ${s.id} step $step at detail $k: things or people came or went"
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `the room goes quiet for its sleepers`() {
        // where there is a lamp (or the cellar's candle, the school's lamps, the smithy's forge) it is put out, turned low or
        // banked: darker with them asleep than without
        fun light(c: PixelCanvas) = c.pixels.sumOf { (Col.r(it) + Col.g(it) + Col.b(it)).toLong() }
        val lamps = mapOf("kitchen" to 2, "livingroom" to 2, "attic" to 2, "workshop" to 3, "cellar" to 1, "smithy" to 1, "school" to 1)
        for (h in homes) for (level in lamps.getValue(h.art)..3) {
            val (asleep, _) = render(h.art, frame(h, level))
            val (awake, _) = render(h.art, frame(h, level, people = emptyList()))
            assertTrue("${h.art} L$level: ${light(asleep)} vs ${light(awake)}", light(asleep) < light(awake) * 0.97)
        }
        // with Jože alone on his run (Janez still at the fire) the living room is as quiet
        val living = homes.first { it.art == "livingroom" }
        val jozeOnly = render("livingroom", frame(living, 2, people = living.sleepers.filter { it.id == "joze" })).first
        assertTrue(light(jozeOnly) < light(render("livingroom", frame(living, 2, people = emptyList())).first) * 0.97)
        // the smith's forge banked for the night: no flames over the coals; a dialog that fires it up has it roar all the same
        val smithy = homes.first { it.art == "smithy" }
        val fired = frame(smithy, 2).copy(fx = mapOf("forge" to 1f))
        assertTrue("smithy: the forge a dialog fires up roars, Tone asleep or not", light(render("smithy", fired).first) > light(render("smithy", frame(smithy, 2)).first) * 1.03)
        // a dialog that lit the attic's lamp or the cellar's candle has it so, sleepers or not
        for ((art, fx) in listOf("attic" to "lamp", "cellar" to "candle")) {
            val h = homes.first { it.art == art }
            val lit = frame(h, 3).copy(fx = mapOf(fx to 1f))
            val (withSleepers, _) = render(art, lit)
            val (without, _) = render(art, lit.copy(people = emptyList()))
            assertTrue("$art: the $fx a dialog lit stays lit", light(withSleepers) > light(render(art, frame(h, 3)).first) * 1.01 && light(without) > 0)
        }
    }

    @Test fun `render the rooms asleep at night`() {
        for (h in homes) {
            for (level in 1..3) {
                val (c, hits, _) = render(h.art, frame(h, level))
                write(c, File(dir, "home-${h.art}-L$level-night.png"), 3)
                // closer up around the sleepers, at detail 3
                val box = h.sleepers.mapNotNull { s -> hits.singleOrNull { it.target == SceneTarget.Person(s.id) } }
                val l = box.minOf { it.left } - 24; val t = box.minOf { it.top } - 30; val r = box.maxOf { it.right } + 24; val b = box.maxOf { it.bottom } + 16
                val (fine, _, _) = render(h.art, frame(h, level), 3)
                crop(fine, l * 3, t * 3, r * 3, b * 3, File(dir, "home-${h.art}-L$level-night-closer.png"), 2)
            }
        }
        // a sheet: each room at levels 1 to 3, asleep and by day
        for (h in homes) {
            val s = 2; val label = 16; val cw = 240; val ch = 170
            val img = BufferedImage(cw * s * 3, (ch * s + label) * 2, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((row, night) in listOf(true, false).withIndex()) for (level in 1..3) {
                val f = if (night) frame(h, level) else frame(h, level, people = emptyList(), hour = 12f)
                val (c, _, _) = render(h.art, f, 1, cw, ch)
                val oy = row * (ch * s + label)
                for (y in 0 until ch * s) for (x in 0 until cw * s) img.setRGB((level - 1) * cw * s + x, oy + label + y, c.pixels[(y / s) * cw + x / s])
                g.color = Color.WHITE
                g.drawString("${h.art}, level $level, ${if (night) "night" else "day"}", (level - 1) * cw * s + 4, oy + 12)
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "home-${h.art}-sheet.png"))
        }
    }

    private fun write(c: PixelCanvas, f: File, s: Int) {
        val img = BufferedImage(c.width * s, c.height * s, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, c.pixels[(y / s) * c.width + x / s])
        ImageIO.write(img, "png", f)
    }

    private fun crop(c: PixelCanvas, l0: Int, t0: Int, r0: Int, b0: Int, f: File, s: Int) {
        val l = l0.coerceIn(0, c.width - 1); val t = t0.coerceIn(0, c.height - 1); val r = r0.coerceIn(l + 1, c.width); val b = b0.coerceIn(t + 1, c.height)
        val img = BufferedImage((r - l) * s, (b - t) * s, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, c.pixels[(t + y / s) * c.width + l + x / s])
        ImageIO.write(img, "png", f)
    }
}
