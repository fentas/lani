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
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Friuli's art requests of places-it-arts (docs/plans/05-built/culture-source.md): NOI | VOI on the bocce court's scoreboard,
 * the osteria with its frasca a thing of the court (`inn`), the playground's fontanella (`fontanella`), the chapel's altar
 * candle apart from the lumini (`altarcandle`), each a thing of Friuli's look only, and the cramâr's crassigne on the road to
 * the palisade's gate and below the watchtower. Contact sheets of each beside Primorska's (build/scene-snapshots/culture-source/), and what they
 * promise: Friuli's picture shows each its own way, each new thing is there to tap once at every detail in Friuli and is none
 * in Primorska, whose pictures draw none of Friuli's (CultureLooksTest holds its pixels to the digests).
 */
class FriuliRequestsLooksTest {
    private val dir = File("build/scene-snapshots/culture-source").apply { mkdirs() }

    private val friuli = SceneWorld(culture = "friuli")
    private val primorska = SceneWorld.ALL

    private fun people(art: String) = SceneArt.personSlots.getValue(art).mapIndexed { k, slot ->
        PersonInScene("p-$slot", listOf("grandpa", "innkeeper", "child2", "aunt", "shepherd")[k % 5], slot)
    }

    private fun frame(
        art: String, world: SceneWorld, hour: Float = 11f, month: Int = 6, fx: Map<String, Float> = emptyMap(), highlight: String? = null,
        who: Boolean = false,
    ) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), world = world, fx = fx, highlight = highlight,
        people = if (who) people(art) else emptyList(),
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(w * k, h * k)
        val hits = ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c to hits
    }

    private class Shot(val label: String, val c: PixelCanvas, val crop: IntArray? = null)

    /** [shots] side by side, [cols] across, each scaled to [cellW] × [cellH] (nearest), its label over it. */
    private fun sheet(name: String, shots: List<Shot>, cols: Int, cellW: Int = 480, cellH: Int = 320) {
        val label = 16
        val rows = (shots.size + cols - 1) / cols
        val img = BufferedImage(cellW * cols, (cellH + label) * rows, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for ((i, s) in shots.withIndex()) {
            val ox = (i % cols) * cellW; val oy = (i / cols) * (cellH + label)
            val (x0, y0, x1, y1) = (s.crop ?: intArrayOf(0, 0, s.c.width, s.c.height)).toList()
            val sw = x1 - x0; val sh = y1 - y0
            val scale = minOf(cellW / sw.toFloat(), cellH / sh.toFloat())
            for (y in 0 until (sh * scale).toInt()) for (x in 0 until (sw * scale).toInt()) {
                val px = x0 + (x / scale).toInt(); val py = y0 + (y / scale).toInt()
                if (px in 0 until s.c.width && py in 0 until s.c.height) img.setRGB(ox + x, oy + label + y, s.c.pixels[py * s.c.width + px])
            }
            g.color = Color.WHITE
            g.drawString(s.label, ox + 4, oy + 12)
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    /** A box round [hit] (in the picture's points: the 240 × 160 canvas's) grown by [pad] on every side, in canvas [c]'s pixels. */
    private fun around(hit: SceneHit, c: PixelCanvas, pad: Int): IntArray {
        val k = c.width / 240
        return intArrayOf(maxOf(0, (hit.left - pad) * k), maxOf(0, (hit.top - pad) * k), minOf(c.width, (hit.right + pad) * k), minOf(c.height, (hit.bottom + pad) * k))
    }

    private fun hitOf(hits: List<SceneHit>, slot: String) = hits.single { it.target == SceneTarget.Thing(slot) }

    /** The box of the pixels where [a] differs from [b] (what an effect drew), grown by [pad]; the whole when none. */
    private fun changed(a: PixelCanvas, b: PixelCanvas, pad: Int): IntArray {
        var x0 = a.width; var y0 = a.height; var x1 = -1; var y1 = -1
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.pixels[y * a.width + x] != b.pixels[y * a.width + x]) {
            x0 = minOf(x0, x); y0 = minOf(y0, y); x1 = maxOf(x1, x); y1 = maxOf(y1, y)
        }
        if (x1 < 0) return intArrayOf(0, 0, a.width, a.height)
        val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2; val half = maxOf(x1 - x0, y1 - y0) / 2 + pad
        return intArrayOf(maxOf(0, cx - half), maxOf(0, cy - half), minOf(a.width, cx + half), minOf(a.height, cy + half))
    }

    @Test fun `render Friuli's requests beside Primorska's`() {
        // the bocce court: the scoreboard's NOI | VOI, the osteria with its frasca to tap, by day, in the evening, in winter
        val bocce = ArrayList<Shot>()
        for ((name, w) in listOf("primorska" to primorska, "friuli" to friuli)) {
            for ((when_, f) in listOf(
                "day" to frame("bocce", w, who = true), "evening" to frame("bocce", w, hour = 20.5f, month = 8, who = true),
                "winter" to frame("bocce", w, hour = 11f, month = 1), "inn" to frame("bocce", w, highlight = "inn"),
            )) bocce += Shot("bocce $name $when_", render("bocce", f).first)
            for (k in 1..3) {
                val (c, hits) = render("bocce", frame("bocce", w, fx = mapOf("score" to 0.6f)), k)
                bocce += Shot("bocce $name scoreboard d$k", c, around(hitOf(hits, "scoreboard"), c, 6))
            }
        }
        sheet("bocce", bocce, 4)
        // the playground's fontanella: by day, in winter (no water), highlighted, closer up
        val play = ArrayList<Shot>()
        for ((name, w) in listOf("primorska" to primorska, "friuli" to friuli)) {
            play += Shot("playground $name day", render("playground", frame("playground", w, who = true)).first)
            play += Shot("playground $name winter", render("playground", frame("playground", w, month = 1)).first)
        }
        for (k in 1..3) {
            val (c, hits) = render("playground", frame("playground", friuli, highlight = "fontanella"), k)
            play += Shot("friuli fontanella d$k", c, around(hitOf(hits, "fontanella"), c, 18))
        }
        play += Shot("friuli fontanella highlighted", render("playground", frame("playground", friuli, highlight = "fontanella")).first)
        sheet("playground", play, 4)
        // the chapel: the altar candle apart from the lumini, out, one lit, all lit, highlighted, closer up, at dusk
        val chapel = ArrayList<Shot>()
        for ((name, w) in listOf("primorska" to primorska, "friuli" to friuli)) {
            for ((label, fx) in listOf("candles 0" to 0f, "candles 0.5" to 0.5f, "candles 1" to 1f)) {
                val (c, hits) = render("chapel", frame("chapel", w, hour = 19.8f, month = 9, fx = mapOf("candles" to fx)), 2)
                chapel += Shot("chapel $name $label (d2)", c, around(hitOf(hits, "altar"), c, 20))
            }
            chapel += Shot("chapel $name altarcandle", render("chapel", frame("chapel", w, highlight = "altarcandle")).first)
            for (k in 1..3) {
                val (c, hits) = render("chapel", frame("chapel", w, highlight = "altarcandle", fx = mapOf("candles" to 1f)), k)
                // (Primorska's altar candle is one thing with the lumini: round the altar there)
                val at = hits.firstOrNull { it.target == SceneTarget.Thing("altarcandle") } ?: hitOf(hits, "altar")
                chapel += Shot("chapel $name altarcandle d$k", c, around(at, c, 24))
            }
        }
        sheet("chapel", chapel, 4)
        // the pedlar on the road to the gate and below the watchtower: Primorska's krošnja, Friuli's crassigne
        val pedlar = ArrayList<Shot>()
        for (art in listOf("palisade", "watchtower")) for ((name, w) in listOf("primorska" to primorska, "friuli" to friuli)) {
            for (p in listOf(0.4f, 0.75f, 0.95f)) {
                pedlar += Shot("$art $name pedlar $p", render(art, frame(art, w, fx = mapOf("pedlar" to p, "gate" to 1f))).first)
            }
            for (p in listOf(0.5f, 0.9f)) for (k in 2..3) {
                val c = render(art, frame(art, w, fx = mapOf("pedlar" to p, "gate" to 1f)), k).first
                val none = render(art, frame(art, w, fx = mapOf("gate" to 1f)), k).first
                pedlar += Shot("$art $name pedlar $p d$k (closer)", c, changed(c, none, 14 * k))
            }
        }
        sheet("pedlar", pedlar, 5)
    }

    @Test fun `Friuli's scoreboard says NOI and VOI, Primorska's MI and VI as it did`() {
        for (k in 1..3) {
            val (a, ha) = render("bocce", frame("bocce", primorska), k)
            val (b, hb) = render("bocce", frame("bocce", friuli), k)
            val boxA = hitOf(ha, "scoreboard"); val boxB = hitOf(hb, "scoreboard")
            // Friuli's slate is a little wider, for the three letters of each side
            assertTrue("d$k: Friuli's slate is wider", boxB.right - boxB.left > boxA.right - boxA.left)
            fun region(c: PixelCanvas) = around(boxA, c, 0).let { (x0, y0, x1, y1) -> (y0 until y1).flatMap { y -> (x0 until x1).map { x -> c.pixels[y * c.width + x] } } }
            assertNotEquals("d$k: the slates differ", region(a), region(b))
        }
    }

    @Test fun `each new thing is there to tap once at every detail in Friuli, none of them in Primorska`() {
        for (k in 1..3) for (hour in listOf(11f, 22.5f)) {
            for ((art, slot) in listOf("bocce" to "inn", "chapel" to "altarcandle", "playground" to "fontanella")) {
                val inFriuli = render(art, frame(art, friuli, hour = hour), k).second
                assertEquals("$art/$slot in Friuli at d$k, $hour h", 1, inFriuli.count { it.target == SceneTarget.Thing(slot) })
                val inPrimorska = render(art, frame(art, primorska, hour = hour), k).second
                assertEquals("$art/$slot not a thing in Primorska at d$k, $hour h", 0, inPrimorska.count { it.target == SceneTarget.Thing(slot) })
            }
        }
    }

    @Test fun `the cramar carries his crassigne in Friuli, the krosnjar his krosnja in Primorska`() {
        for (art in listOf("palisade", "watchtower")) for (p in listOf(0.5f, 0.9f)) {
            val fx = mapOf("pedlar" to p, "gate" to 1f)
            val withA = render(art, frame(art, primorska, fx = fx), 2).first.pixels
            val withB = render(art, frame(art, friuli, fx = fx), 2).first.pixels
            val noneB = render(art, frame(art, friuli, fx = fx - "pedlar"), 2).first.pixels
            val noneA = render(art, frame(art, primorska, fx = fx - "pedlar"), 2).first.pixels
            // where the pedlar is drawn, the two cultures draw him otherwise
            val his = withB.indices.filter { withB[it] != noneB[it] || withA[it] != noneA[it] }
            assertTrue("$art at $p: the pedlar is drawn", his.isNotEmpty())
            assertTrue("$art at $p: the cramâr is not the krošnjar", his.count { withA[it] != withB[it] && withA[it] != noneA[it] } > 4)
        }
    }

    @Test fun `the palisade's gate in Friuli at every level still draws the cramar`() {
        for (lv in 1..5) {
            val w = friuli.copy(here = Building("palisade", BuildingType.PALISADE, 0, level = lv))
            val a = render("palisade", frame("palisade", w, fx = mapOf("pedlar" to 0.9f, "gate" to 1f))).first.pixels
            val b = render("palisade", frame("palisade", w, fx = mapOf("gate" to 1f))).first.pixels
            assertTrue("level $lv", a.indices.any { a[it] != b[it] })
        }
    }
}
