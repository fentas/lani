package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The mill's water in the scene's light (art "mill", [MillPainter]; docs/plans/05-built/mill-mechanics.md): the water in the
 * flume, in the buckets, pouring off the flume and off the buckets, and the race's, drawn in its daylight colours and lit by
 * the light pass as the wood of the wheel and the flume beside it is (the room's ambient, the lantern's and the window's
 * light where they reach), so at night all of it takes the night's light, once; by day under a clear sky nothing changed.
 * Frames for review in build/scene-snapshots/mill-light.
 */
class MillLightTest {
    private val dir = File("build/scene-snapshots/mill-light").apply { mkdirs() }
    private val bw = 270; private val bh = 297
    private val art = "mill"

    /** October: no fireflies at night, no snow; the same month by day and at night, so only the light differs. */
    private fun frame(hour: Float, time: Double = 3.0, grind: Float? = null, month: Int = 10, culture: String = "primorska") = SceneFrame(
        time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(),
        fx = grind?.let { mapOf("grind" to it) }.orEmpty(), world = SceneWorld.ALL.copy(culture = culture),
    )

    private class Shot(val p: MillPainter, val c: PixelCanvas, val hits: List<SceneHit>)

    private fun shot(f: SceneFrame, k: Int): Shot {
        val p = ScenePainters.create(art) as MillPainter
        val c = PixelCanvas(bw * k, bh * k)
        return Shot(p, c, p.render(c, f, Lens(k, 0, 0, bw, bh)))
    }

    /** The picture round the waterwheel and the flume's end at detail [k], [m] scene px over. */
    private fun around(s: Shot, k: Int, m: Int = 14): IntArray {
        val hs = s.hits.filter { (it.target as? SceneTarget.Thing)?.slot in setOf("wheel", "flume") }
        return intArrayOf(max(0, (hs.minOf { it.left } - m) * k), max(0, (hs.minOf { it.top } - m) * k), min(s.c.width, (hs.maxOf { it.right } + m) * k), min(s.c.height, (hs.maxOf { it.bottom } + m) * k))
    }

    /** Water's colour, lit or not: blue over red, or the white of its foam and glints. */
    private fun wet(col: Int): Boolean {
        val r = (col shr 16) and 0xFF; val g = (col shr 8) and 0xFF; val b = col and 0xFF
        return b > r + 8 || (min(r, min(g, b)) > 190 && b >= r - 12)
    }

    /**
     * The pixels round the wheel by what they show, in the day shot [d] (the night shot [n] drawn at the same moment, the same
     * things in the same places): the flume's water, the buckets', the water falling (off the flume's end into the buckets,
     * off the buckets into the race), the race's, and the wood of the wheel and the flume (never wet, neither lit up nor
     * glowing): picture indices.
     */
    private fun kinds(d: Shot, n: Shot, k: Int): Map<String, List<Int>> {
        val w = d.c.width
        val bx = around(d, k)
        val wheelIds = HashSet<Int>()
        for (py in bx[1] until bx[3]) for (px in bx[0] until bx[2]) if (d.p.targetAt(d.c, px, py, 0) == SceneTarget.Thing("wheel")) wheelIds += d.c.ids[py * w + px]
        val first = wheelIds.min(); val last = wheelIds.max()
        val out = HashMap<String, ArrayList<Int>>()
        for (py in bx[1] until bx[3]) for (px in bx[0] until bx[2]) {
            val i = py * w + px
            // the same thing at the same place in both, nothing glowing over it
            if (d.c.ids[i] != n.c.ids[i] || d.c.emissive[i] || n.c.emissive[i]) continue
            val id = d.c.ids[i]
            val target = d.p.targetAt(d.c, px, py, 0)
            val kind = when {
                !wet(d.c.pixels[i]) -> if (target == SceneTarget.Thing("wheel") || target == SceneTarget.Thing("flume")) "wood" else null
                target == SceneTarget.Thing("flume") -> "flume"
                id in first..last -> "buckets"
                id > last -> "falling"
                id > 0 -> "race"
                else -> null
            } ?: continue
            out.getOrPut(kind) { ArrayList() } += i
        }
        return out
    }

    /** How much of each of red, green and blue the pixels [ix] keep from [d] to [n], all of them together. */
    private fun kept(d: Shot, n: Shot, ix: List<Int>): FloatArray {
        val sd = LongArray(3); val sn = LongArray(3)
        for (i in ix) for (ch in 0..2) {
            val sh = 16 - 8 * ch
            sd[ch] += ((d.c.pixels[i] shr sh) and 0xFF).toLong(); sn[ch] += ((n.c.pixels[i] shr sh) and 0xFF).toLong()
        }
        return FloatArray(3) { sn[it].toFloat() / max(1L, sd[it]).toFloat() }
    }

    private fun fmt(v: FloatArray) = v.joinToString(", ", "(", ")") { "%.3f".format(it) }

    @Test fun `the flume's and the buckets' water at night take the night's light as the wood beside them does`() {
        for (k in 1..3) for (grind in listOf<Float?>(null, 1f)) {
            val d = shot(frame(11f, grind = grind), k); val n = shot(frame(22.5f, grind = grind), k)
            val ks = kinds(d, n, k)
            val wood = kept(d, n, ks.getValue("wood"))
            println("detail $k grind $grind: wood ${fmt(wood)} (${ks.getValue("wood").size} px)" + listOf("flume", "buckets", "falling", "race").joinToString("") { name ->
                ks[name]?.let { ", $name ${fmt(kept(d, n, it))} (${it.size} px)" } ?: ", $name none"
            })
            // the night is dark: the wood keeps about a third of its red, half its blue
            assertTrue("detail $k: the night darkens the wood ${fmt(wood)}", wood[0] < 0.45f && wood[2] < 0.65f && wood[2] > wood[0])
            for (name in listOf("flume", "buckets", "falling", "race")) {
                val ix = ks[name].orEmpty()
                assertTrue("detail $k grind $grind: the $name's water drawn (${ix.size} px)", ix.size > 12 * k)
                val got = kept(d, n, ix)
                for (ch in 0..2) assertEquals("detail $k grind $grind: the $name's water ${fmt(got)} lit as the wood ${fmt(wood)}, channel $ch", wood[ch], got[ch], 0.035f + 0.1f * wood[ch])
            }
        }
    }

    /** 64-bit FNV-1a over the pixels of [c] in [box]. */
    private fun fnv(c: PixelCanvas, box: IntArray): String {
        var h = -0x340d631b7bdddcdbL
        for (y in box[1] until box[3]) for (x in box[0] until box[2]) { h = h xor (c.pixels[y * c.width + x].toLong() and 0xffffffffL); h *= 0x100000001b3L }
        return java.lang.Long.toHexString(h)
    }

    /** The wheel, its water and the flume's end by day, as they were drawn before their water took the scene's light (2026-11-01). */
    private val byDay = mapOf(
        "d1" to "36922f0bdfb18b1c", "d1-hard" to "f118bd53ab2e2617", "d2" to "4094dd50d8ceee8d", "d2-hard" to "eef4fadffd447f04",
        "d3" to "86895ba3bfb7884e", "d3-hard" to "3bdac49604ae9f62", "d2-friuli" to "78537145156e9863", "d2-summer" to "b21a54e72a2e7617",
    )

    @Test fun `by day under a clear sky the wheel's water is drawn as it was`() {
        val got = LinkedHashMap<String, String>()
        for (k in 1..3) for (hard in listOf(false, true)) {
            val s = shot(frame(11f, grind = if (hard) 1f else null), k)
            got["d$k" + if (hard) "-hard" else ""] = fnv(s.c, around(s, k))
        }
        shot(frame(11f, culture = "friuli"), 2).let { got["d2-friuli"] = fnv(it.c, around(it, 2)) }
        shot(frame(14f, month = 7), 2).let { got["d2-summer"] = fnv(it.c, around(it, 2)) }
        println(got.entries.joinToString(", ") { "\"${it.key}\" to \"${it.value}\"" })
        assertEquals(byDay, got)
    }

    // ------------------------------------------------------------------ for review

    private fun write(c: PixelCanvas, box: IntArray, scale: Int, f: File) {
        val img = BufferedImage((box[2] - box[0]) * scale, (box[3] - box[1]) * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until (box[3] - box[1]) * scale) for (x in 0 until (box[2] - box[0]) * scale) img.setRGB(x, y, c.pixels[(box[1] + y / scale) * c.width + box[0] + x / scale])
        ImageIO.write(img, "png", f)
    }

    @Test fun `render the wheel's water by day, at dusk and at night for review`() {
        val views = listOf("day" to (11f to 10), "dusk" to (18.6f to 10), "night" to (22.5f to 10), "night-summer" to (23f to 7), "night-winter" to (22.5f to 1))
        for (culture in listOf("primorska", "friuli")) for ((view, hm) in views) for (k in 1..3) {
            val s = shot(frame(hm.first, month = hm.second, culture = culture, grind = 1f), k)
            write(s.c, around(s, k), 6 / k, File(dir, "$culture-$view-d$k.png"))
            if (k == 1) write(s.c, intArrayOf(0, 0, s.c.width, s.c.height), 3, File(dir, "$culture-$view-whole.png"))
        }
    }
}
