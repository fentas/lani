package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.CultureLooks
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.Sky
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Friuli's outdoor scenes in its own look (companion/SCENES.md "Culture looks"): the field, the wood, the stream, the pond
 * and the sea of Teo's village rendered for review beside Primorska's (build/scene-snapshots/looks/friuli-out: a sheet per
 * art, the views down, Primorska on the left, Friuli inland and on the coast on the right; every view closer up too).
 */
class FriuliOutdoorLooksTest {
    private val dir = File("build/scene-snapshots/looks/friuli-out").apply { mkdirs() }
    private val arts = listOf("field", "forest", "stream", "pond", "sea")

    private fun world(culture: String, landscape: String = "classic") = SceneWorld(culture = culture, landscape = landscape)

    private fun people(art: String) = SceneArt.personSlots.getValue(art).mapIndexed { k, slot -> PersonInScene("p-$slot", SceneArt.people[k % SceneArt.people.size], slot) }

    /** The views of an art: the hours and the seasons, the weather, someone at every spot by day. */
    private fun views(art: String): List<Pair<String, (SceneWorld) -> SceneFrame>> {
        val objects = SceneArt.objects.getValue(art).toSet()
        fun f(hour: Float, month: Int, sky: Sky = Sky.CLEAR, who: Boolean = false): (SceneWorld) -> SceneFrame =
            { w -> SceneFrame(time = 3.0, hour = hour, month = month, objects = objects, people = if (who) people(art) else emptyList(), world = w, sky = sky) }
        return listOf(
            "summer-day" to f(11f, 7, who = true),
            "spring-morning" to f(8.5f, 4),
            "autumn-afternoon" to f(15.5f, 10),
            "winter-day" to f(12f, 1),
            "summer-evening" to f(19.8f, 8),
            "night" to f(23f, 6),
            "fog" to f(9f, 11, Sky(fog = 0.6f, gloom = 0.3f)),
        )
    }

    private fun render(f: SceneFrame, art: String, bw: Int = 240, bh: Int = 160, k: Int = 1): PixelCanvas {
        val c = PixelCanvas(bw * k, bh * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, bw, bh))
        return c
    }

    private fun write(c: PixelCanvas, name: String, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    /** A sheet of [cells] (rows of canvases of one size), [scale] times, 4 px apart. */
    private fun sheet(cells: List<List<PixelCanvas>>, name: String, scale: Int) {
        val cw = cells[0][0].width; val ch = cells[0][0].height; val gap = 4
        val cols = cells.maxOf { it.size }
        val img = BufferedImage((cw * scale + gap) * cols, (ch * scale + gap) * cells.size, BufferedImage.TYPE_INT_RGB)
        for ((r, row) in cells.withIndex()) for ((col, c) in row.withIndex()) {
            val x0 = col * (cw * scale + gap); val y0 = r * (ch * scale + gap)
            for (y in 0 until ch * scale) for (x in 0 until cw * scale) img.setRGB(x0 + x, y0 + y, c.pixels[(y / scale) * c.width + x / scale])
        }
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    @Test fun `render the Friuli outdoor arts for review`() {
        val worlds = listOf("primorska" to world("primorska"), "friuli" to world("friuli"), "friuli-coast" to world("friuli", "coast"))
        for (art in arts) {
            val rows = views(art).map { (_, f) -> worlds.map { (_, w) -> render(f(w), art) } }
            sheet(rows, "$art-sheet", 2)
            for ((view, f) in views(art)) for ((name, w) in worlds.drop(1)) {
                for (k in 2..Lens.MAX_DETAIL) write(render(f(w), art, k = k), "$art-$name-$view-d$k", 1)
            }
            // a tall view (a tablet held upright) and a wide one, by day
            val day = views(art).first().second
            sheet(listOf(worlds.map { (_, w) -> render(day(w), art, 180, 290) }), "$art-tall", 2)
            sheet(listOf(worlds.map { (_, w) -> render(day(w), art, 384, 160) }), "$art-wide", 2)
        }
    }

    @Test fun `render what the words name in Friuli's look for review`() {
        val w = world("friuli"); val p = world("primorska")
        fun f(art: String, world: SceneWorld, who: List<PersonInScene> = emptyList(), fx: Map<String, Float> = emptyMap(), count: Int? = null, hour: Float = 10f, month: Int = 9) =
            SceneFrame(time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), people = who, world = world, fx = fx, count = count)
        // Aldo's basket ("guarda nel mio cesto"), three and nine mushrooms, closer up too
        val aldo = listOf(PersonInScene("aldo", "beekeeper", "clearing", talking = true))
        for (n in listOf(3, 9, 12)) for (k in 1..3) write(render(f("forest", w, aldo, count = n), "forest", k = k), "words-forest-basket-$n-d$k", 4 - k)
        // Nonno Bepi's tin of worms by Moro ("vicino a Moro"), and while he fishes
        val bepi = listOf(PersonInScene("bepi", "grandpa", "jetty"))
        for (k in 1..3) write(render(f("pond", w, bepi, mapOf("dog" to 0.1f, "bite" to 0f), hour = 15f, month = 6), "pond", k = k), "words-pond-tin-d$k", 4 - k)
        write(render(f("pond", w, bepi, mapOf("dog" to 0.9f, "bite" to 1f), hour = 15f, month = 6), "pond"), "words-pond-catch", 3)
        // the halyard ("Vedi la corda lì?"), the sail furled and hoisted
        for (sail in listOf(0f, 1f)) for (k in 1..3) write(render(f("sea", w, listOf(PersonInScene("bepi", "grandpa", "boat")), mapOf("sail" to sail), hour = 9f, month = 7), "sea", k = k), "words-sea-halyard-${(sail * 10).toInt()}-d$k", 4 - k)
        // the fienile with its hay and without, before the village builds it, and Primorska's kozolec beside it; the big barn's steps
        val unbuilt = w.copy(buildings = emptyMap())
        sheet(listOf(listOf(render(f("field", p, hour = 11f, month = 7), "field"), render(f("field", w, hour = 11f, month = 7), "field"), render(f("field", unbuilt, hour = 11f, month = 7), "field"))), "words-field-fienile", 2)
        val toplar = (0..6).map { st -> render(f("field", w.copy(projects = mapOf("toplar" to st)), hour = 11f, month = 7), "field") }
        sheet(listOf(toplar.take(4), toplar.drop(4)), "words-field-toplar", 2)
        for (k in 1..3) write(render(f("field", w, listOf(PersonInScene("franco", "farmer", "field"), PersonInScene("lorenzo", "child3", "path")), mapOf("horse" to 0.5f), hour = 11f, month = 7), "field", k = k), "words-field-baio-d$k", 4 - k)
        // the ancona by day and at night, closer up
        for (k in 1..3) for (hour in listOf(11f, 22.5f)) write(render(f("forest", w, hour = hour, month = 5), "forest", k = k), "words-forest-ancona-${hour.toInt()}-d$k", 4 - k)
        // the stream's mill and bridge as the village builds them, in Friuli's look
        val mill = (0..6).map { st -> render(f("stream", w.copy(projects = mapOf("mlin" to st, "most" to st)), hour = 11f, month = 7), "stream") }
        sheet(listOf(mill.take(4), mill.drop(4)), "words-stream-projects", 2)
    }

    // ------------------------------------------------------------------ what the look draws, and what it keeps

    private fun frameOf(art: String, w: SceneWorld, hour: Float = 11f, month: Int = 7, fx: Map<String, Float> = emptyMap(), who: List<PersonInScene> = emptyList(), count: Int? = null, time: Double = 3.0) =
        SceneFrame(time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), people = who, world = w, fx = fx, count = count)

    private fun hits(f: SceneFrame, art: String, k: Int = 1, bw: Int = 240, bh: Int = 160): Map<SceneTarget, SceneHit> {
        val c = PixelCanvas(bw * k, bh * k)
        return ScenePainters.create(art).render(c, f, Lens(k, 0, 0, bw, bh)).associateBy { it.target }
    }

    private fun changed(a: PixelCanvas, b: PixelCanvas) = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }

    @Test fun `Friuli's look changes each of its outdoor arts, inland and on the coast, and touches them in the look`() {
        val look = CultureLooks.of("friuli")
        for (art in arts) {
            assertTrue("$art in Friuli's look", look.touches(art))
            for (land in listOf("classic", "coast")) for ((hour, month) in listOf(11f to 7, 22.5f to 1)) {
                val p = render(frameOf(art, world("primorska", land), hour, month), art)
                val f = render(frameOf(art, world("friuli", land), hour, month), art)
                // the sea's own look is Friuli's: only the batela's halyard is new there; the wood's is its ancona
                val least = when (art) { "sea" -> 20; "forest" -> 100; else -> 400 }
                assertTrue("$art on $land at $hour h: Friuli's look draws something of its own (${changed(p, f)} px)", changed(p, f) > least)
            }
        }
    }

    @Test fun `in Friuli's look every thing can be tapped and everyone stands at their spot, at every detail and size`() {
        for (art in arts) for (land in listOf("classic", "coast")) {
            val f = frameOf(art, world("friuli", land), who = people(art))
            val base = hits(f, art)
            // every thing and everyone of Primorska's picture is there in Friuli's
            val theirs = hits(frameOf(art, world("primorska", land), who = people(art)), art).keys
            assertEquals("$art on $land: the things and the people to tap", theirs, base.keys)
            for (p in people(art)) assertTrue("$art/${p.slot} on $land", base.containsKey(SceneTarget.Person(p.id)))
            for (k in 2..Lens.MAX_DETAIL) assertEquals("$art on $land at detail $k: the same things and people", base.keys, hits(f, art, k).keys)
            for ((bw, bh) in listOf(180 to 290, 384 to 160)) {
                val pf = frameOf(art, world("primorska", land), who = people(art))
                val mine = hits(f, art, 1, bw, bh); val theirs2 = hits(pf, art, 1, bw, bh)
                assertEquals("$art on $land at $bw x $bh: ${theirs2.filterKeys { it !in mine }} ${mine.filterKeys { it !in theirs2 }}", theirs2.keys, mine.keys)
            }
        }
    }

    @Test fun `Friuli's field has its fienile where the kozolec stands, the hay in it, the rake against its corner`() {
        val w = world("friuli")
        val h = hits(frameOf("field", w), "field")
        val barn = h.getValue(SceneTarget.Thing("kozolec")); val hay = h.getValue(SceneTarget.Thing("hay")); val rake = h.getValue(SceneTarget.Thing("rake"))
        val kozolec = hits(frameOf("field", world("primorska")), "field").getValue(SceneTarget.Thing("kozolec"))
        // where the kozolec stood, a little taller with its roof
        assertTrue("the fienile stands where the kozolec does: $barn vs $kozolec", abs((barn.left + barn.right) - (kozolec.left + kozolec.right)) <= 8 && barn.bottom in kozolec.bottom - 2..kozolec.bottom + 2)
        // the hay inside it, big enough to tap
        assertTrue("the hay is in the fienile", hay.left >= barn.left - 4 && hay.right <= barn.right + 4 && hay.top >= barn.top && hay.bottom <= barn.bottom + 2)
        assertTrue("the hay is big enough to tap: $hay", (hay.right - hay.left) >= 12 && (hay.bottom - hay.top) >= 8)
        // the rake leans on its right corner
        assertTrue("the rake by the fienile's corner: $rake vs $barn", rake.left <= barn.right + 6 && rake.right >= barn.right - 10)
        // without a KOZOLEC built, no fienile and no hay (as the kozolec)
        val none = hits(frameOf("field", w.copy(buildings = emptyMap())), "field")
        assertFalse(none.containsKey(SceneTarget.Thing("kozolec"))); assertFalse(none.containsKey(SceneTarget.Thing("hay")))
    }

    @Test fun `Aldo's basket holds as many mushrooms as the dialog counts, only while it counts, by whoever stands at the clearing`() {
        val w = world("friuli")
        val aldo = listOf(PersonInScene("aldo", "beekeeper", "clearing"))
        val none = render(frameOf("forest", w, who = aldo), "forest")
        val three = render(frameOf("forest", w, who = aldo, count = 3), "forest")
        val nine = render(frameOf("forest", w, who = aldo, count = 9), "forest")
        assertTrue("a basket comes with the count", changed(none, three) > 30)
        assertTrue("nine mushrooms aren't three", changed(three, nine) > 6)
        // Primorska keeps its picture with the count (its own look draws no basket)
        val p = world("primorska")
        assertEquals(0, changed(render(frameOf("forest", p, who = aldo), "forest"), render(frameOf("forest", p, who = aldo, count = 9), "forest")))
        // nobody at the clearing: no basket
        assertEquals(0, changed(render(frameOf("forest", w), "forest"), render(frameOf("forest", w, count = 9), "forest")))
    }

    @Test fun `Friuli's ancona's lumino burns at night and lights the path`() {
        val w = world("friuli")
        val p = world("primorska")
        val night = changed(render(frameOf("forest", p, hour = 22.5f), "forest"), render(frameOf("forest", w, hour = 22.5f), "forest"))
        assertTrue("the shrine and its light at night: $night px", night > 60)
    }

    @Test fun `Nonno Bepi's tin of worms lies by Moro in Friuli's pond`() {
        val w = world("friuli")
        val bepi = listOf(PersonInScene("bepi", "grandpa", "jetty"))
        val dog = mapOf("dog" to 0.1f, "bite" to 0f)
        val without = render(frameOf("pond", w, who = bepi), "pond")
        val with = render(frameOf("pond", w, who = bepi, fx = dog), "pond")
        val withoutP = render(frameOf("pond", world("primorska"), who = bepi), "pond")
        val withP = render(frameOf("pond", world("primorska"), who = bepi, fx = dog), "pond")
        // Moro and the tin come in Friuli's picture; in Primorska's only Muri
        assertTrue("Moro and the tin", changed(without, with) > changed(withoutP, withP) + 10)
        // fishing alone brings the tin too
        val fishing = render(frameOf("pond", w, who = bepi, fx = mapOf("bite" to 0.3f)), "pond")
        val fishingP = render(frameOf("pond", world("primorska"), who = bepi, fx = mapOf("bite" to 0.3f)), "pond")
        assertTrue("the tin while Bepi fishes", changed(without, fishing) > changed(withoutP, fishingP) + 10)
    }

    @Test fun `the batela's halyard runs from its masthead to the cleat in Friuli's sea`() {
        val f = render(frameOf("sea", world("friuli")), "sea")
        val p = render(frameOf("sea", world("primorska")), "sea")
        val boat = hits(frameOf("sea", world("friuli")), "sea").getValue(SceneTarget.Thing("boat"))
        val boatP = hits(frameOf("sea", world("primorska")), "sea").getValue(SceneTarget.Thing("boat"))
        assertTrue("the rope is drawn", changed(f, p) > 20)
        // the rope is the boat's: tapping it is tapping the boat
        assertTrue("the boat's tap area takes the rope: $boat vs $boatP", boat.top <= boatP.top)
    }

    @Test fun `Friuli's effects still play in its look`() {
        for (art in arts) {
            val w = world("friuli")
            for (name in SceneArt.effects.getValue(art)) {
                val lo = render(frameOf(art, w, fx = mapOf(name to 0f), who = people(art), hour = 18.5f, month = 9), art)
                val hi = render(frameOf(art, w, fx = mapOf(name to 1f), who = people(art), hour = 18.5f, month = 9), art)
                val pLo = render(frameOf(art, world("primorska"), fx = mapOf(name to 0f), who = people(art), hour = 18.5f, month = 9), art)
                val pHi = render(frameOf(art, world("primorska"), fx = mapOf(name to 1f), who = people(art), hour = 18.5f, month = 9), art)
                // what shows in Primorska's picture shows in Friuli's
                if (changed(pLo, pHi) > 0) assertTrue("$art/$name plays in Friuli's look", changed(lo, hi) > 0)
            }
        }
    }

    @Test fun `nothing of Friuli's look is drawn over the head of someone at a person spot`() {
        val failed = ArrayList<String>()
        val sprites = SceneArt.people - setOf("baby", "pedlar", "pilgrim")
        for (art in arts) for (slot in SceneArt.personSlots.getValue(art)) {
            if ("$art/$slot" == "forest/highseat") continue
            for (sprite in sprites) for (hour in listOf(15f, 22.5f)) {
                val p = ScenePainters.create(art) as ArtPainter
                p.keepPeople = true
                val c = PixelCanvas(270, 297)
                val f = SceneFrame(time = 3.0, hour = hour, month = 7, objects = SceneArt.objects.getValue(art).toSet(), world = world("friuli"),
                    wild = si.lanisce.lani.game.scene.Wildlife.all(art), people = listOf(PersonInScene("p", sprite, slot)), count = 9)
                p.render(c, f, Lens(1, 0, 0, c.width, c.height))
                val d = p.drawnPeople.firstOrNull { it.id == "p" } ?: run { failed += "$art/$slot: $sprite not drawn"; null } ?: continue
                val top = d.at.minOf { it / c.width }; val bottom = d.at.maxOf { it / c.width }
                val line = top + ((bottom - top + 1) * 0.4f).toInt()
                if (d.covered.any { it / c.width < line }) failed += "$art/$slot: $sprite at $hour h"
            }
        }
        assertTrue("drawn over a head:\n" + failed.joinToString("\n"), failed.isEmpty())
    }

    @Test fun `a frame of Friuli's outdoor arts renders fast`() {
        for (art in arts) {
            val p = ScenePainters.create(art)
            val f = SceneFrame(time = 1.0, hour = 22.5f, month = 10, objects = SceneArt.objects.getValue(art).toSet(), people = people(art), world = world("friuli", "coast"), highlight = SceneArt.objects.getValue(art).first())
            val c = PixelCanvas(360, 240)
            repeat(15) { p.render(c, f.copy(time = it / 12.0)) }
            val n = 40
            val t0 = System.nanoTime()
            repeat(n) { p.render(c, f.copy(time = 2 + it / 12.0)) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("Friuli %s: %.2f ms/frame (night, 360x240)".format(art, ms))
            assertTrue("$art too slow: $ms ms", ms < FrameBudget.scaled(20.0))
        }
    }

    // ------------------------------------------------------------------ Friuli's pictures, kept

    /** 64-bit FNV-1a over the pixels. */
    private fun fnv(ints: IntArray): String {
        var h = -0x340d631b7bdddcdbL
        for (v in ints) { h = h xor (v.toLong() and 0xffffffffL); h *= 0x100000001b3L }
        return java.lang.Long.toHexString(h)
    }

    /**
     * Friuli's outdoor pictures as built on 2026-10-28, inland and on the coast: by day with someone at every spot and
     * everything built, by day before anything is (the fienile not yet, the mill and the bridges not begun), a winter's
     * dusk in a tall view, a night closer up, a summer's afternoon at the closest. A change to one of them is a change to
     * Friuli's look: look at the review renders, then take the new digests from the test's output.
     */
    private fun friuliDigests(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (art in arts) for (land in listOf("classic", "coast")) {
            val all = world("friuli", land)
            val none = SceneWorld.NONE.copy(culture = "friuli", landscape = land)
            val objects = SceneArt.objects.getValue(art).toSet()
            val views = listOf(
                Triple("day", SceneFrame(time = 3.0, hour = 11f, month = 6, objects = objects, people = people(art), world = all), Lens(1, 0, 0, 240, 160)),
                Triple("day-unbuilt", SceneFrame(time = 3.0, hour = 11f, month = 6, objects = objects, world = none), Lens(1, 0, 0, 240, 160)),
                Triple("dusk-winter", SceneFrame(time = 5.0, hour = 18.5f, month = 1, objects = objects, world = all), Lens(1, 0, 0, 270, 297)),
                Triple("night-k2", SceneFrame(time = 7.0, hour = 22.5f, month = 10, objects = objects, people = people(art), world = all), Lens(2, 120, 80, 240, 160)),
                Triple("afternoon-k3", SceneFrame(time = 9.0, hour = 15f, month = 8, objects = objects, world = all, count = 7), Lens(3, 180, 150, 240, 160)),
            )
            for ((name, f, lens) in views) {
                val c = PixelCanvas(lens.baseW, lens.baseH)
                ScenePainters.create(art).render(c, f, lens)
                out["$art/$land/$name"] = fnv(c.pixels)
            }
        }
        return out
    }

    private val golden: Map<String, String> = mapOf(
        "field/classic/day" to "46a3fcb02112ca22",
        "field/classic/day-unbuilt" to "c28bd133703883b2",
        "field/classic/dusk-winter" to "81910872d516f56",
        "field/classic/night-k2" to "9368eb8965f19962",
        "field/classic/afternoon-k3" to "3a924b936f6b69a2",
        "field/coast/day" to "46a3fcb02112ca22",
        "field/coast/day-unbuilt" to "c28bd133703883b2",
        "field/coast/dusk-winter" to "81910872d516f56",
        "field/coast/night-k2" to "72d982e740ef25b3",
        "field/coast/afternoon-k3" to "bd96d687fc0bf17d",
        "forest/classic/day" to "917830871af2b779",
        "forest/classic/day-unbuilt" to "5a8fb4ab9f01ec66",
        "forest/classic/dusk-winter" to "f4532a55c215c252",
        "forest/classic/night-k2" to "2f330679144b3e16",
        "forest/classic/afternoon-k3" to "9314086eeb27d90e",
        "forest/coast/day" to "917830871af2b779",
        "forest/coast/day-unbuilt" to "5a8fb4ab9f01ec66",
        "forest/coast/dusk-winter" to "f4532a55c215c252",
        "forest/coast/night-k2" to "2f330679144b3e16",
        "forest/coast/afternoon-k3" to "9314086eeb27d90e",
        "stream/classic/day" to "38abb935ad53d60a",
        "stream/classic/day-unbuilt" to "512cdcec17f6ae41",
        "stream/classic/dusk-winter" to "1fba2c1a3f69077e",
        "stream/classic/night-k2" to "e77a65440fe25c1b",
        "stream/classic/afternoon-k3" to "8c83a25a60699651",
        "stream/coast/day" to "38abb935ad53d60a",
        "stream/coast/day-unbuilt" to "512cdcec17f6ae41",
        "stream/coast/dusk-winter" to "1fba2c1a3f69077e",
        "stream/coast/night-k2" to "25d0ef99361a1c97",
        "stream/coast/afternoon-k3" to "8c83a25a60699651",
        "pond/classic/day" to "c2b3d0f661f38dcd",
        "pond/classic/day-unbuilt" to "40a6a8ed193539ba",
        "pond/classic/dusk-winter" to "6c3c45dae116dfaa",
        "pond/classic/night-k2" to "3b4bdca28725bdc3",
        "pond/classic/afternoon-k3" to "4764c53df98116b",
        "pond/coast/day" to "c2b3d0f661f38dcd",
        "pond/coast/day-unbuilt" to "40a6a8ed193539ba",
        "pond/coast/dusk-winter" to "6c3c45dae116dfaa",
        "pond/coast/night-k2" to "3b4bdca28725bdc3",
        "pond/coast/afternoon-k3" to "4764c53df98116b",
        "sea/classic/day" to "7afd33e510411d4a",
        "sea/classic/day-unbuilt" to "22d7ddb01bf8927d",
        "sea/classic/dusk-winter" to "cb5cd4f543ead1fd",
        "sea/classic/night-k2" to "c85d92fb7587d0c8",
        "sea/classic/afternoon-k3" to "d0533e774173c131",
        "sea/coast/day" to "7afd33e510411d4a",
        "sea/coast/day-unbuilt" to "22d7ddb01bf8927d",
        "sea/coast/dusk-winter" to "cb5cd4f543ead1fd",
        "sea/coast/night-k2" to "c85d92fb7587d0c8",
        "sea/coast/afternoon-k3" to "d0533e774173c131",
    )

    @Test fun `Friuli's outdoor pictures are as built`() {
        val got = friuliDigests()
        val changed = got.filter { (k, v) -> golden[k] != v }
        if (changed.isNotEmpty()) println(changed.entries.joinToString(",\n") { "\"${it.key}\" to \"${it.value}\"" })
        assertEquals("Friuli's pictures drawn otherwise than built", emptySet<String>(), changed.keys)
    }
}
