package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Villager
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The people on the village map at their day's work, drawn at every level of detail: the hoe, the anvil, carrying hay,
 * wood, water and a basket, tending, washing at the bank, children at play, a chat, someone waving to the learner, and
 * the lantern at night with its warm light. Each is tappable, moves, and a lantern lights the ground round it. PNG
 * snapshots in build/village-snapshots for review: 70-activities-{day,night} (all of them at k = 1, 2, 3 side by side),
 * 71-activities-sheet… (each person over ten moments).
 */
class MapActivitiesTest {
    private val dir = File("build/village-snapshots").apply { mkdirs() }
    private val fit = SceneFit.town(1080, 2400)
    private val rig = CameraRig.town(fit, 1080, 2400)
    private val comp = VillageLayout.composition(fit.width, fit.height, false)
    private val today = "2026-09-24"

    /** A village with room in front of the fire, and nobody else about. */
    private val village = GameState(
        seed = 42, age = Age.VAS, villagers = 0, morale = 70, fire = 70,
        buildings = listOf(BuildingType.FIELD, BuildingType.HUT, BuildingType.WELL).mapIndexed { i, t -> Building("b$i", t, i) },
    )

    private class Who(val id: String, val art: String, val activity: Activity, val load: Load = Load.NONE, val lantern: Boolean = false, val flip: Boolean = false)

    /** Five to a row: each activity, the loads, the lantern in the hand and set down, and one standing as they always have. */
    private val cast = listOf(
        Who("hoe", "farmer", Activity.HOE), Who("hammer", "smith", Activity.HAMMER), Who("tend", "beekeeper", Activity.TEND), Who("wash", "woman", Activity.WASH), Who("stand", "shepherd", Activity.STAND),
        Who("hay", "man", Activity.CARRY, Load.HAY), Who("wood", "grandpa", Activity.CARRY, Load.WOOD), Who("water", "aunt", Activity.CARRY, Load.WATER), Who("basket", "woman", Activity.CARRY, Load.BASKET, flip = true), Who("play1", "child1", Activity.PLAY),
        Who("play2", "child2", Activity.PLAY, flip = true), Who("chat1", "grandma", Activity.CHAT), Who("chat2", "man", Activity.CHAT, flip = true), Who("wave", "innkeeper", Activity.WAVE), Who("walk-lantern", "man", Activity.WALK, lantern = true),
        Who("stand-lantern", "woman", Activity.STAND, lantern = true, flip = true), Who("chat-lantern", "grandpa", Activity.CHAT, lantern = true), Who("wave-lantern", "teacher", Activity.WAVE, lantern = true, flip = true), Who("carry-lantern", "aunt", Activity.CARRY, Load.WATER, lantern = true), Who("sit-lantern", "shepherd", Activity.SIT, lantern = true, flip = true),
    )

    private val cols = 5
    private val rows get() = (cast.size + cols - 1) / cols
    private val dx = 20f; private val dy = 16f

    /** The middle of the cast in nominal screen px from the fire: the open grass east of it. */
    private val ox = 54f; private val oy = 0f

    /** Where [i] of the cast stands: nominal screen px from the fire, turned into the world's cells. */
    private fun at(i: Int): FloatArray {
        val sx = ox + (i % cols - (cols - 1) / 2f) * dx; val sy = oy + (i / cols - (rows - 1) / 2f) * dy
        return floatArrayOf((sx / 4f + sy / 2f) / 2f, (sy / 2f - sx / 4f) / 2f)
    }

    private fun placed(lanterns: Boolean = true) = cast.mapIndexed { i, w ->
        val p = at(i)
        Placed(Villager(id = w.id, name = w.id, art = w.art), p[0], p[1], w.activity, w.flip, TownPlace.Fire, lantern = w.lantern && lanterns, load = w.load)
    }

    /** The feet of [i] on the nominal canvas. */
    private fun feet(i: Int): FloatArray { val p = at(i); return floatArrayOf(comp.fireX + (p[0] - p[1]) * 4f, comp.fireY + (p[0] + p[1]) * 2f) }

    /** The middle of the cast, nominal px. */
    private val mid = floatArrayOf(comp.fireX + ox, comp.fireY + oy)

    private fun lens(k: Int): Lens = Lens.of(k, rig.lookAt(mid[0], mid[1], (rig.minScale * k).toFloat()), 1080, 2400, fit.width, fit.height, fit.width, fit.height)

    private fun render(r: VillageRenderer, k: Int, hour: Float, time: Double, lanterns: Boolean = true): PixelCanvas {
        val c = PixelCanvas(fit.width, fit.height)
        r.render(c, village, Frame(time = time, hour = hour, month = 9, today = today, placed = placed(lanterns), lens = lens(k)))
        return c
    }

    /** The object id [id] was drawn with (searched round their feet), and the box to look for their pixels in. */
    private fun mine(r: VillageRenderer, c: PixelCanvas, l: Lens, i: Int): Pair<Int, IntArray> {
        val f = feet(i); val k = l.k
        val x = l.cx(f[0]).toInt(); val y = l.cy(f[1]).toInt()
        val box = intArrayOf(x - 12 * k, y - 16 * k, x + 12 * k, y + 2 * k)
        for (yy in y - 2 * k downTo y - 8 * k) for (xx in x - 2 * k..x + 2 * k) {
            if (r.hitTest(xx, yy, 0) == VillageHit.OnVillager(cast[i].id)) return c.ids[yy * c.width + xx] to box
        }
        error("${cast[i].id} not found at k=$k")
    }

    /** [i]'s own pixels: where in the box, and their colours. */
    private fun pixels(c: PixelCanvas, id: Int, box: IntArray): Map<Int, Int> {
        val m = HashMap<Int, Int>()
        for (y in box[1].coerceAtLeast(0)..box[3].coerceAtMost(c.height - 1)) for (x in box[0].coerceAtLeast(0)..box[2].coerceAtMost(c.width - 1)) {
            val j = y * c.width + x
            if (c.ids[j] == id) m[j] = c.pixels[j]
        }
        return m
    }

    @Test fun `everyone at their work is drawn where they stand and tappable at every level of detail`() {
        for (k in 1..3) {
            val r = VillageRenderer()
            val c = render(r, k, 11f, 2.0)
            val l = r.lens
            for ((i, w) in cast.withIndex()) {
                val f = feet(i)
                assertEquals("${w.id} at k=$k", VillageHit.OnVillager(w.id), r.hitTest(l.cx(f[0]).toInt(), l.cy(f[1]).toInt() - 3 * k, 3 * k))
                assertTrue("${w.id}'s bubble at k=$k", r.villagerAnchors.containsKey(w.id))
                val (id, box) = mine(r, c, l, i)
                assertTrue("${w.id} drawn at k=$k", pixels(c, id, box).size > 8 * k * k)
            }
        }
    }

    @Test fun `the people at work move`() {
        for (k in 1..3) {
            val r = VillageRenderer()
            val seen = HashMap<String, MutableSet<Map<Int, Int>>>()
            // every quarter second over the waving's 8 s round
            for (n in 0 until 34) {
                val c = render(r, k, 11f, 1.0 + n * 0.25)
                for ((i, w) in cast.withIndex()) {
                    val (id, box) = mine(r, c, r.lens, i)
                    // the shape of them (a hand up, the hammer down), not the light on them
                    seen.getOrPut(w.id) { HashSet() } += pixels(c, id, box).mapValues { 0 }
                }
            }
            for (w in cast) if (w.activity != Activity.STAND && w.activity != Activity.SIT) {
                assertTrue("${w.id} (${w.activity}) moves at k=$k: ${seen[w.id]?.size} shapes", (seen[w.id]?.size ?: 0) >= 2)
            }
        }
    }

    @Test fun `a lantern at night glows and lights the ground round it`() {
        for (k in 1..3) {
            val r = VillageRenderer()
            val lit = render(r, k, 23f, 2.0)
            val ids = cast.indices.associateWith { mine(r, lit, r.lens, it) }
            val dark = render(VillageRenderer(), k, 23f, 2.0, lanterns = false)
            val l = r.lens
            for ((i, w) in cast.withIndex()) {
                val (id, box) = ids.getValue(i)
                val glowing = pixels(lit, id, box).keys.count { lit.emissive[it] }
                if (w.lantern) assertTrue("${w.id}'s lantern glows at k=$k", glowing > 0)
                else if (w.activity != Activity.HAMMER) assertEquals("${w.id} has no lantern at k=$k", 0, glowing)
                if (!w.lantern) continue
                // the ground and whatever stands round them, 4 to 9 nominal px from the feet, is brighter with the lantern
                val f = feet(i); val x = l.cx(f[0]); val y = l.cy(f[1])
                var withL = 0.0; var without = 0.0; var n = 0
                for (yy in (y - 9 * k).toInt()..(y + 9 * k).toInt()) for (xx in (x - 9 * k).toInt()..(x + 9 * k).toInt()) {
                    val dd = Math.hypot((xx - x).toDouble(), (yy - y).toDouble()) / k
                    if (dd < 4 || dd > 9 || xx !in 0 until lit.width || yy !in 0 until lit.height) continue
                    withL += Col.lum(lit.pixels[yy * lit.width + xx]); without += Col.lum(dark.pixels[yy * lit.width + xx]); n++
                }
                assertTrue("${w.id}'s lantern lights the ground at k=$k: ${withL / n} vs ${without / n}", withL / n > without / n * 1.15)
            }
        }
    }

    @Test fun `render the day's work and the lanterns at night at every level of detail`() {
        for ((name, hour) in listOf("day" to 11f, "night" to 23f)) {
            val shots = (1..3).map { k ->
                val r = VillageRenderer(); val c = render(r, k, hour, 2.0)
                if (k == 1) {
                    val full = BufferedImage(c.width * 2, c.height * 2, BufferedImage.TYPE_INT_RGB)
                    for (y in 0 until c.height * 2) for (x in 0 until c.width * 2) full.setRGB(x, y, c.pixels[(y / 2) * c.width + x / 2])
                    ImageIO.write(full, "png", File(dir, "70-activities-$name-full.png"))
                }
                crop(c, r.lens)
            }
            for ((k, img) in shots.withIndex()) ImageIO.write(img, "png", File(dir, "70-activities-$name-k${k + 1}.png"))
            val all = BufferedImage(shots.sumOf { it.width } + 16, shots[0].height, BufferedImage.TYPE_INT_RGB)
            var x0 = 0
            for (img in shots) { all.graphics.drawImage(img, x0, 0, null); x0 += img.width + 8 }
            ImageIO.write(all, "png", File(dir, "70-activities-$name.png"))
        }
        // each of them over ten moments 0.8 s apart (the waving's 8 s round, the strikes), at every detail; the lanterns at night
        for (k in 1..3) for ((part, group) in cast.indices.chunked(cols).withIndex()) {
            val moments = (0 until 10).map { n -> val r = VillageRenderer(); val c = render(r, k, if (part == rows - 1) 23f else 11f, 1.0 + n * 0.8); c to r.lens }
            val f = if (k == 1) 8 else if (k == 2) 4 else 3
            val tw = 26 * k * f; val th = 17 * k * f
            val sheet = BufferedImage(10 * (tw + 4), group.size * (th + 4), BufferedImage.TYPE_INT_RGB)
            for ((row, i) in group.withIndex()) for ((n, m) in moments.withIndex()) {
                val (c, l) = m
                val fx = l.cx(feet(i)[0]).toInt(); val fy = l.cy(feet(i)[1]).toInt()
                for (y in 0 until th) for (x in 0 until tw) {
                    val cx = (fx - 13 * k + x / f).coerceIn(0, c.width - 1); val cy = (fy - 14 * k + y / f).coerceIn(0, c.height - 1)
                    sheet.setRGB(n * (tw + 4) + x, row * (th + 4) + y, c.pixels[cy * c.width + cx])
                }
            }
            ImageIO.write(sheet, "png", File(dir, "71-activities-sheet$part-k$k.png"))
        }
    }

    /** The cast's patch of the canvas, the same patch of the world at every detail, blown up to the same size. */
    private fun crop(c: PixelCanvas, l: Lens, s: Int = 6): BufferedImage {
        val x0 = mid[0] - (cols - 1) / 2f * dx - 10f; val x1 = mid[0] + (cols - 1) / 2f * dx + 10f
        val y0 = mid[1] - (rows - 1) / 2f * dy - 16f; val y1 = mid[1] + (rows - 1) / 2f * dy + 4f
        val w = ((x1 - x0) * s).toInt(); val h = ((y1 - y0) * s).toInt()
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) {
            val cx = l.cx(x0 + x.toFloat() / s).toInt().coerceIn(0, c.width - 1); val cy = l.cy(y0 + y.toFloat() / s).toInt().coerceIn(0, c.height - 1)
            img.setRGB(x, y, c.pixels[cy * c.width + cx])
        }
        return img
    }
}
