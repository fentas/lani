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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The mill's drive train and its water (art "mill", [MillPainter]; companion/SCENES.md "Arts"), from the pixels drawn: the
 * waterwheel and the cog wheel on one shaft turn together, their tops downstream; the cog wheel's pegs drive the pinion and
 * the runner round the way the mesh has it, three times as fast; the flume's water falls into the buckets just past the
 * top and rides down the downstream side into the race, never over the wheel's face or its hub. Frames of the turning wheel
 * for review in build/scene-snapshots/mill-mechanics (a bucket's travel from the flume down to the race, by day, at night
 * and in winter, Primorska's and Friuli's look, at every detail; the stream's mill and the map's).
 */
class MillMechanicsTest {
    private val dir = File("build/scene-snapshots/mill-mechanics").apply { mkdirs() }
    private val bw = 270; private val bh = 297
    private val art = "mill"

    private fun frame(time: Double, hour: Float, month: Int, culture: String, fx: Map<String, Float> = emptyMap()) = SceneFrame(
        time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), fx = fx,
        world = SceneWorld.ALL.copy(culture = culture),
    )

    private fun render(f: SceneFrame, k: Int): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(bw * k, bh * k)
        return c to ScenePainters.create(art).render(c, f, Lens(k, 0, 0, bw, bh))
    }

    /**
     * The part of the picture round the turning things (the wheel, the flume, the shaft, the cog wheel, the stones) at detail
     * [k], [m] scene px over (the hits are in the scene's canvas px).
     */
    private fun around(hits: List<SceneHit>, k: Int, m: Int = 8): IntArray {
        val slots = setOf("wheel", "flume", "shaft", "gear", "millstone")
        val hs = hits.filter { (it.target as? SceneTarget.Thing)?.slot in slots }
        return intArrayOf((hs.minOf { it.left } - m) * k, (hs.minOf { it.top } - m) * k, (hs.maxOf { it.right } + m) * k, (hs.maxOf { it.bottom } + m) * k)
    }

    private fun write(c: PixelCanvas, box: IntArray?, scale: Int, f: File) {
        val l = box?.get(0)?.coerceAtLeast(0) ?: 0; val t = box?.get(1)?.coerceAtLeast(0) ?: 0
        val r = box?.get(2)?.coerceAtMost(c.width) ?: c.width; val b = box?.get(3)?.coerceAtMost(c.height) ?: c.height
        val img = BufferedImage((r - l) * scale, (b - t) * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until (b - t) * scale) for (x in 0 until (r - l) * scale) img.setRGB(x, y, c.pixels[(t + y / scale) * c.width + l + x / scale])
        ImageIO.write(img, "png", f)
    }

    // ------------------------------------------------------------------ the physics, from the pixels

    private class Shot(val p: MillPainter, val c: PixelCanvas, val hits: List<SceneHit>)

    /** The mill drawn at detail [k]; [solo]: that thing alone (ArtPainter.solo: nothing over it, unlit). */
    private fun shot(f: SceneFrame, k: Int, solo: String? = null): Shot {
        val p = ScenePainters.create(art) as MillPainter
        p.solo = solo
        val c = PixelCanvas(bw * k, bh * k)
        return Shot(p, c, p.render(c, f, Lens(k, 0, 0, bw, bh)))
    }

    private fun day(time: Double, grind: Float? = null) = frame(time, 11f, 6, "primorska", grind?.let { mapOf("grind" to it) }.orEmpty())

    /** [slot]'s hit box in the picture at detail [k] (the hits are in the scene's canvas px), [m] picture px over. */
    private fun box(s: Shot, slot: String, k: Int, m: Int = 0): IntArray {
        val h = s.hits.first { it.target == SceneTarget.Thing(slot) }
        return intArrayOf(max(0, h.left * k - m), max(0, h.top * k - m), min(s.c.width, h.right * k + m), min(s.c.height, h.bottom * k + m))
    }

    /**
     * The turn (radians, within ±[range]) that, undone, lays the most of [pts] (picture px of shot b: x, y, then where they lie
     * as [at] has it) on the same colour in shot [a]: [at] (pixel's own place, the turn) gives the picture point in a.
     */
    private fun bestTurn(a: Shot, b: Shot, pts: List<FloatArray>, range: Float, at: (FloatArray, Float) -> FloatArray): Float {
        var best = 0f; var most = -1
        val n0 = (range / 0.005f).toInt()
        // from no turn outward, so that of turns laying them equally well the least wins
        for (i in (0..2 * n0).map { if (it % 2 == 0) it / 2 else -(it + 1) / 2 }) {
            val d = i * 0.005f
            var n = 0
            for (q in pts) {
                val s = at(q, d)
                val ax = floor(s[0]).toInt(); val ay = floor(s[1]).toInt()
                if (ax in 0 until a.c.width && ay in 0 until a.c.height && a.c.pixels[ay * a.c.width + ax] == b.c.pixels[q[1].toInt() * b.c.width + q[0].toInt()]) n++
            }
            if (n > most) { most = n; best = d }
        }
        return best
    }

    /**
     * How far (radians) [slot] (drawn alone) turned on its wheel's face [face] (its plane x, the axle's y and height, the
     * radius, px a cell along y) from shot [a] to shot [b], from its pixels [zone] (px from the axle) holds: its arms and pegs,
     * not the rim's light and shade, which stay where they are. Positive: its marks went from +z toward +y, the top downstream.
     */
    private fun turned(a: Shot, b: Shot, slot: String, face: FloatArray, k: Int, zone: (Float) -> Boolean): Float {
        val x = face[0]; val y0 = face[1]; val z0 = face[2]; val cz = face[4]
        val bx = box(b, slot, k)
        val pts = ArrayList<FloatArray>()
        for (py in bx[1] until bx[3]) for (px in bx[0] until bx[2]) {
            if (b.p.targetAt(b.c, px, py, 0) != SceneTarget.Thing(slot)) continue
            val q = b.p.onPlaneX(px, py, x)
            val dy = (q[0] - y0) * cz; val dz = q[1] - z0
            val rr = hypot(dy, dz)
            if (zone(rr)) pts += floatArrayOf(px.toFloat(), py.toFloat(), rr, atan2(dz, dy))
        }
        assertTrue("$slot: enough of it to see it turn (${pts.size} px)", pts.size > 150)
        // the mark at φ in b was at φ + d in a
        return bestTurn(a, b, pts, 0.5f) { q, d -> a.p.pictureOf(x, y0 + cos(q[3] + d) * q[2] / cz, z0 + sin(q[3] + d) * q[2]) }
    }

    /** How far (radians) the runner's grooved top (drawn alone) turned from [a] to [b], in the picture's ellipse of it: positive, its near side going left. */
    private fun runnerTurned(a: Shot, b: Shot): Float {
        val (cx, cy, rx) = b.p.runnerShape(); val ry = rx * 0.5f
        val pts = ArrayList<FloatArray>()
        for (py in floor(cy - ry).toInt()..ceil(cy + ry).toInt()) for (px in floor(cx - rx).toInt()..ceil(cx + rx).toInt()) {
            val u = (px + 0.5f - cx) / rx; val v = (py + 0.5f - cy) / ry
            val rho = hypot(u, v)
            if (rho < 0.3f || rho > 0.9f || b.p.targetAt(b.c, px, py, 0) != SceneTarget.Thing("millstone")) continue
            pts += floatArrayOf(px.toFloat(), py.toFloat(), rho, atan2(v, u))
        }
        assertTrue("the runner: enough of it to see it turn (${pts.size} px)", pts.size > 150)
        // the groove at ψ in b was at ψ − d in a (within half the way from a groove to the next)
        return bestTurn(a, b, pts, 0.38f) { q, d -> floatArrayOf(cx + cos(q[3] - d) * q[2] * rx, cy + sin(q[3] - d) * q[2] * ry) }
    }

    /** The columns of the pinion's staves halfway up between its discs, in the picture of the gearing alone. */
    private fun staves(s: Shot): List<Int> {
        val (cx, y0, y1) = s.p.pinionShape()
        val w = s.c.width
        val id = s.c.ids[floor(y1).toInt() * w + floor(cx).toInt()]
        assertTrue("the pinion's top disc shows", id != 0)
        val row = ((y0 + y1) / 2f).toInt()
        return (0 until w).filter { s.c.ids[row * w + it] == id }
    }

    /** The waterwheel's arms (px from its axle), inside its rim; the cog wheel's arms and its pegs. */
    private val wheelArms = { rr: Float -> rr in 1.6f..6.6f }
    private val cogArmsAndPegs = { rr: Float -> rr in 1.0f..4.6f || rr in 5.9f..6.9f }

    @Test fun `the waterwheel and the cog wheel turn together on their shaft, their tops going downstream`() {
        val k = 2
        for ((grind, spin) in listOf(null to 0.7f, 0.3f to 0.7f, 1f to 1.7f)) {
            // a quarter radian: less than half the way from an arm to the next, of either wheel
            val dt = 0.25 / spin
            val turns = listOf("wheel", "gear").map { slot ->
                val a = shot(day(3.0, grind), k, slot); val b = shot(day(3.0 + dt, grind), k, slot)
                if (slot == "wheel") turned(a, b, slot, a.p.waterwheelFace(), k, wheelArms) else turned(a, b, slot, a.p.cogWheelFace(), k, cogArmsAndPegs)
            }
            // positive: the marks at the top go toward +y, downstream, the way the race runs and the water falls
            assertEquals("the waterwheel at grind $grind: its top downstream at the shaft's speed", spin * dt.toFloat(), turns[0], 0.02f)
            assertEquals("the cog wheel at grind $grind: the same turn as the waterwheel on its shaft", turns[0], turns[1], 0.015f)
        }
        // stopped: neither moves
        for (slot in listOf("wheel", "gear")) {
            val a = shot(day(3.0, 0f), k, slot); val b = shot(day(3.4, 0f), k, slot)
            val t = if (slot == "wheel") turned(a, b, slot, a.p.waterwheelFace(), k, wheelArms) else turned(a, b, slot, a.p.cogWheelFace(), k, cogArmsAndPegs)
            assertEquals("$slot stopped", 0f, t, 0.001f)
        }
    }

    @Test fun `the cog wheel's pegs drive the pinion and the runner round as they mesh, three times as fast`() {
        val k = 3
        // the cog wheel a quarter radian on, by itself
        val dt = 0.25 / 0.7
        val g0 = shot(day(3.0), k, "gear"); val g1 = shot(day(3.0 + dt), k, "gear")
        val cog = g0.p.cogWheelFace()
        val gear = turned(g0, g1, "gear", cog, k, cogArmsAndPegs) / dt.toFloat()
        assertTrue("the cog wheel's top goes downstream (+y): $gear", gear > 0f)
        // they mesh at the cog wheel's top, its pegs against the pinion's side toward its face: the spindle stands behind
        // the face (−x of it), so the pegs push the pinion's +x side downstream with them, and the pinion turns from +x
        // toward +y; in the picture its near side goes left, its angle in the picture's ellipse (θ + π/4) grows as θ does
        assertTrue("the pinion's spindle stands behind the cog wheel's face", g0.p.pinionAxis()[0] < cog[0])
        // the runner on its spindle, a third of the way from a groove to the next
        val rt = 0.12f
        val r0 = shot(day(3.0), k, "millstone"); val r1 = shot(day(3.0 + rt), k, "millstone")
        val runner = runnerTurned(r0, r1) / rt
        assertEquals("the runner on the pinion's spindle: from +x toward +y, three times the cog wheel's speed", 3f * gear, runner, 0.25f)
        // the pinion's staves (drawn with the cog wheel: the platform hides it in the picture): the near ones go left
        val a = shot(day(3.0), k, "gear"); val b = shot(day(3.0 + rt), k, "gear")
        val (cx, _, _, rx) = a.p.pinionShape()
        val before = staves(a); val after = staves(b)
        val moved = before.filter { abs(it + 0.5f - cx) < 0.55f * rx }.map { x -> after.minByOrNull { abs(it - x) }!! - x }
        assertTrue("the pinion's near staves seen: $before", moved.isNotEmpty())
        assertTrue("the pinion's near staves go left: $moved", moved.all { it <= 0 } && moved.sum() < 0)
    }

    /** Water's colour, lit or not: blue over red, or the white of its foam and glints. */
    private fun wet(col: Int): Boolean {
        val r = (col shr 16) and 0xFF; val g = (col shr 8) and 0xFF; val b = col and 0xFF
        return b > r + 8 || (min(r, min(g, b)) > 190 && b >= r - 12)
    }

    @Test fun `the flume's water falls into the buckets just past the top and rides down the downstream side, never over the wheel's face`() {
        val k = 2
        for ((grind, spin) in listOf(null to 0.7, 1f to 1.7)) {
            // a moment the shaft has turned whole eighths, its arms and buckets where they stand when it is still
            val time = ceil(3.0 * spin / (PI / 4)) * (PI / 4) / spin
            val run = shot(day(time, grind), k); val still = shot(day(time, 0f), k)
            val w = run.c.width
            val face = run.p.waterwheelFace(); val r = face[3]
            val wheel = (0 until still.c.pixels.size).filter { still.p.targetAt(still.c, it % w, it / w, 0) == SceneTarget.Thing("wheel") }.map { still.c.ids[it] }
            val first = wheel.min(); val last = wheel.max()
            val bx = box(run, "wheel", k, 14 * k)
            var faceChanged = 0; var buckets = 0; var falling = 0; var poured = 0; var spilled = 0
            val upstream = ArrayList<String>()
            for (py in bx[1] until bx[3]) for (px in bx[0] until bx[2]) {
                val i = py * w + px
                val q = run.p.onPlaneX(px, py, face[0])
                val dy = (q[0] - face[1]) * face[4]; val dz = q[1] - face[2]
                val sid = still.c.ids[i]; val rid = run.c.ids[i]
                if (hypot(dy, dz) < r - 2.4f && sid <= last) {
                    // the face inside the buckets' ring (its arms, the hub, what shows between them): drawn as when it stands
                    // still, nothing after the wheel over it
                    if (rid != sid) faceChanged += 10
                    else if (sid >= first && run.c.pixels[i] != still.c.pixels[i]) faceChanged++
                    continue
                }
                if (!wet(run.c.pixels[i]) || wet(still.c.pixels[i])) continue
                if (rid in first..last) {
                    // the buckets' water, between the shrouds over the near one's edge
                    val iq = run.p.onPlaneX(px, py, run.p.innerShroud)
                    val idy = (iq[0] - face[1]) * face[4]
                    buckets++
                    if (idy <= 0f) upstream += "bucket water at dy %.1f dz %.1f".format(idy, iq[1] - face[2])
                } else if (rid > last && run.p.targetAt(run.c, px, py, 0) != SceneTarget.Thing("flume")) {
                    // the water off the flume and off the buckets, in the plane between the shrouds
                    val wq = run.p.onPlaneX(px, py, run.p.waterPlane)
                    val wdy = (wq[0] - face[1]) * face[4]; val wdz = wq[1] - face[2]
                    falling++
                    if (wdy <= 0f) upstream += "falling water at dy %.1f dz %.1f".format(wdy, wdz)
                    if (wdz > r - 1.5f && wdy < 3f) poured++
                    if (wdz < -1f && wdy > 6f) spilled++
                }
            }
            println("grind $grind: buckets $buckets px, falling $falling px (poured $poured, spilled $spilled), the face changed $faceChanged")
            assertTrue("grind $grind: the wheel's face and hub drawn over ($faceChanged)", faceChanged <= 4)
            assertEquals("grind $grind: water upstream of the axle", emptyList<String>(), upstream.take(10))
            assertTrue("grind $grind: the buckets on the way down hold water ($buckets px)", buckets > 60)
            assertTrue("grind $grind: the flume's water pours into the buckets just past the top ($poured px)", poured > 15)
            assertTrue("grind $grind: the buckets empty into the race low on the downstream side ($spilled px)", spilled > 15)
        }
    }

    // ------------------------------------------------------------------ for review

    @Test fun `render the turning mill for review`() {
        val views = listOf("day" to (11f to 6), "night" to (22.5f to 10), "winter" to (11f to 1))
        for (culture in listOf("primorska", "friuli")) for ((view, hm) in views) for (k in 1..Lens.MAX_DETAIL) {
            // a bucket's travel at an easy turn (0.7 rad/s): from under the flume (its top) down to the race, about 2 rad
            for (i in 0 until 8) {
                val (c, hits) = render(frame(3.0 + i * 0.35, hm.first, hm.second, culture), k)
                write(c, around(hits, k), 6 / k, File(dir, "$culture-$view-d$k-f$i.png"))
                if (i == 0) write(c, null, 3, File(dir, "$culture-$view-d$k-whole.png"))
            }
        }
        // the three frames CultureLooksTest pins (by day with someone at every spot, a winter's dusk, a night closer up)
        val people = SceneArt.personSlots.getValue(art).mapIndexed { k, slot -> si.lanisce.lani.game.scene.PersonInScene("p-$slot", SceneArt.people[k % SceneArt.people.size], slot) }
        val world = SceneWorld(culture = si.lanisce.lani.game.culture.Cultures.DEFAULT)
        for ((name, f, lens) in listOf(
            Triple("day", frame(3.0, 11f, 6, "primorska").copy(people = people, world = world), Lens(1, 0, 0, 240, 160)),
            Triple("dusk-winter", frame(5.0, 18.5f, 1, "primorska").copy(world = world), Lens(1, 0, 0, 270, 297)),
            Triple("night-k2", frame(7.0, 22.5f, 10, "primorska").copy(people = people, world = world), Lens(2, 120, 80, 240, 160)),
        )) {
            val c = PixelCanvas(lens.baseW, lens.baseH)
            ScenePainters.create(art).render(c, f, lens)
            write(c, null, 3, File(dir, "pinned-$name.png"))
        }
        // grinding hard, and stopped (the water off the flume, everything still)
        for (culture in listOf("primorska", "friuli")) for ((name, g) in listOf("hard" to 1f, "stopped" to 0f)) for (i in 0 until 8) {
            val (c, hits) = render(frame(3.0 + i * 0.15, 11f, 6, culture, mapOf("grind" to g)), 2)
            write(c, around(hits, 2), 3, File(dir, "$culture-$name-d2-f$i.png"))
        }
    }

    @Test fun `render the stream's mill and the map's for review`() {
        // the stream's mill (art "stream"): its wheel under the flume, by day and in winter, closer up
        for (culture in listOf("primorska", "friuli")) for ((view, hm) in listOf("day" to (11f to 6), "winter" to (11f to 1))) for (k in 2..3) for (i in 0 until 6) {
            val f = SceneFrame(time = 3.0 + i * 0.3, hour = hm.first, month = hm.second, objects = SceneArt.objects.getValue("stream").toSet(), world = SceneWorld.ALL.copy(culture = culture))
            val c = PixelCanvas(240 * k, 160 * k)
            val hits = ScenePainters.create("stream").render(c, f, Lens(k, 0, 0, 240, 160))
            val mill = hits.first { it.target == SceneTarget.Thing("mill") }
            write(c, intArrayOf((mill.left - 30) * k, (mill.top - 4) * k, (mill.right + 4) * k, (mill.bottom + 10) * k), 6 / k, File(dir, "stream-$culture-$view-d$k-f$i.png"))
        }
        // the village map's mill (the project "mlin" done), closest up
        val fit = si.lanisce.lani.game.render.SceneFit.town(1080, 2400)
        val s = si.lanisce.lani.game.GameState(
            seed = 42, age = si.lanisce.lani.game.Age.ZASELEK, villagers = 10, morale = 70, fire = 70,
            projects = mapOf("mlin" to si.lanisce.lani.game.Catalog.projects.first { it.id == "mlin" }.steps.size),
        )
        val sp = si.lanisce.lani.game.render.LandmarkLayout.of(s, fit.width, fit.height).single()
        val comp = si.lanisce.lani.game.render.VillageLayout.composition(fit.width, fit.height, false)
        val r = sp.screenRect()
        val cx = comp.fireX + (r[0] + r[2]) / 2f; val cy = comp.fireY + (r[1] + r[3]) / 2f
        val k = 3; val cw = 56 * k; val ch = 60 * k
        for (i in 0 until 6) {
            val c = PixelCanvas(cw, ch)
            val frame = si.lanisce.lani.game.render.Frame(time = 3.0 + i * 0.25, hour = 11f, month = 7, lens = Lens(k, (cx * k - cw / 2f).toInt(), (cy * k - ch / 2f).toInt(), fit.width, fit.height))
            si.lanisce.lani.game.render.VillageRenderer().render(c, s, frame)
            write(c, null, 3, File(dir, "map-mlin-d3-f$i.png"))
        }
    }
}
