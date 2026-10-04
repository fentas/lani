package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WATCHTOWER
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.scene.TownPlace
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * The palisade's ring round the clearing: where it stands, where it opens, that a tap anywhere on it opens it, and PNG
 * snapshots in build/village-snapshots (50-…) for visual review.
 */
class PalisadeTest {
    private val fit = SceneFit.town(1080, 2400)
    private val ages = Age.entries.filter { it >= Age.TABOR }

    private fun village(age: Age, vararg types: BuildingType) = GameState(
        seed = 42, age = age, villagers = 6, morale = 70, fire = 70,
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i) } + Building("p", PALISADE, NO_PLOT),
    )

    /** The ring of [age] without and with the woods below the village (a tall canvas: the path gets a gate). */
    private fun rings(age: Age) = listOf(null, Foreground.of(age, fit.width, fit.height)!!).map { it to Palisade.ring(Terrain.CLASSIC, age, it) }

    private fun roadHalf(age: Age) = if (age >= Age.VAS) 1f else 0.55f

    @Test fun `the ring stands between the outermost plot and the forest's edge, all the way round`() {
        var last = 0f
        for (age in ages) for ((_, ring) in rings(age)) {
            val plots = (0 until PLOTS_PER_AGE[age.ordinal]).maxOf { Terrain.CLASSIC.centers[it][2] }
            var sum = 0f
            for (st in ring.stakes) {
                val sx = (st.x - st.y) * 4f; val sy = (st.x + st.y) * 2f
                val m = VillageLayout.screenMetric(sx, sy); val a = VillageLayout.angle(sx, sy)
                // straight left of the fire the edge's noise meets itself in a step: the ring steps between both sides
                val edge = if (abs(abs(a) - PI) < 0.05) max(Terrain.CLASSIC.forestEdge(age, PI.toFloat()), Terrain.CLASSIC.forestEdge(age, -PI.toFloat()))
                else Terrain.CLASSIC.forestEdge(age, a)
                // along the stream the ring keeps to its far bank, where no trees grow: it may step out there, and on the
                // stretch where it crosses to the far bank
                val bank = st.x - Terrain.CLASSIC.streamX(st.y) < 4f
                assertTrue("$age: a stake at metric $m among the plots (out to $plots)", m > plots + 8f)
                assertTrue("$age: a stake at metric $m past the forest's edge $edge", m < edge + (if (bank) 36f else 0.5f))
                sum += m
            }
            // round the whole clearing: the stakes follow each other closely, but for the gates
            val gaps = ring.stakes.indices.map { i -> val a = ring.stakes[i]; val b = ring.stakes[(i + 1) % ring.stakes.size]; hypot(a.x - b.x, a.y - b.y) }
            assertTrue("$age: a gap of ${gaps.max()} cells", gaps.max() < 5.5f)
            assertEquals("$age: gaps but at the gates", ring.gates.size, gaps.count { it > Palisade.STEP * 1.8f })
            // it grows with the clearing
            val mean = sum / ring.stakes.size
            assertTrue("$age: mean $mean", mean >= last)
            last = mean
        }
    }

    @Test fun `the gates stand where the road and the path leave the clearing, a water gate where the stream goes through`() {
        for (age in ages) for ((fg, ring) in rings(age)) {
            // the main gate on the road to the right, a post either side of it
            val main = ring.main!!
            val g = ring.gatePoint()
            assertTrue("$age: the main gate at ${g.toList()}", abs(g[1]) < roadHalf(age) && g[0] > 5f)
            val a = ring.stakes[main.a]; val b = ring.stakes[main.b]
            assertTrue(a.post && b.post)
            assertTrue("$age: posts at ${a.y} and ${b.y}", a.y < -roadHalf(age) && b.y > roadHalf(age) || b.y < -roadHalf(age) && a.y > roadHalf(age))
            // from Zaselek on the road leaves to the left over the bridge too
            assertEquals(if (age >= Age.ZASELEK) 2 else 1, ring.gates.count { it.kind == PalisadeRing.Kind.ROAD })
            // on a tall canvas, a gate on the path down into the woods
            val paths = ring.gates.filter { it.kind == PalisadeRing.Kind.PATH }
            if (fg == null) assertTrue(paths.isEmpty()) else {
                val p = ring.middle(paths.single())
                assertTrue("$age: the path's gate at ${p.toList()}", fg.onPath(fg.comp.fireX + (p[0] - p[1]) * 4f, fg.comp.fireY + (p[0] + p[1]) * 2f, slack = 1f))
            }
            for (w in ring.gates.filter { it.kind == PalisadeRing.Kind.WATER }.map { ring.middle(it) }) {
                assertTrue("$age: a water gate off the stream at ${w.toList()}", abs(w[0] - Terrain.CLASSIC.streamX(w[1])) < 1.2f)
            }
            // every gate between two posts
            for (gate in ring.gates) assertTrue(ring.stakes[gate.a].post && ring.stakes[gate.b].post)
        }
        // the stream runs through the ring from the camp on, in at the top and out at the left: a water gate each time
        for (age in ages) for ((_, ring) in rings(age)) assertEquals("$age", 2, ring.gates.count { it.kind == PalisadeRing.Kind.WATER })
    }

    /**
     * Along the stream the ring keeps to one bank from one gate to the next, never switching between them, and the
     * stakes follow each other a stake's spacing apart but at the gates (road, path, river gate, water gate): no hole.
     * From the camp on, where the stream runs along the clearing's rim, the ring takes it in and keeps to its far bank all
     * the way along it, the bridge's gate on that bank, and crosses the water only at the two water gates.
     */
    @Test fun `along the stream the ring keeps to one bank between its gates, and has no hole but its gates`() {
        for (age in Age.entries) for (fg in listOf(null, Foreground.of(age, fit.width, fit.height))) {
            val ring = Palisade.ring(Terrain.CLASSIC, age, fg)
            val n = ring.stakes.size
            val gates = ring.gates.map { setOf(it.a, it.b) }
            fun d(st: PalisadeRing.Stake) = st.x - Terrain.CLASSIC.streamX(st.y)
            for (k in 0 until n) {
                val a = ring.stakes[k]; val b = ring.stakes[(k + 1) % n]
                if (setOf(k, (k + 1) % n) in gates) continue
                val gap = hypot(a.x - b.x, a.y - b.y)
                assertTrue("$age: a hole of $gap cells after stake $k at (${a.x}, ${a.y})", gap <= Palisade.STEP * 1.2f)
                if (abs(d(a)) < 6f && abs(d(b)) < 6f) assertEquals("$age: stakes $k and ${k + 1} on both banks of the stream at (${a.x}, ${a.y})", d(a) > 0f, d(b) > 0f)
            }
            if (age < Age.TABOR) continue
            // on the village's bank close by the stream only where the ring comes down to its water gates
            val water = ring.gates.filter { it.kind == PalisadeRing.Kind.WATER }.flatMap { listOf(it.a, it.b) }
            for ((k, st) in ring.stakes.withIndex()) if (d(st) > 0f && d(st) < 3.5f) {
                val off = water.minOf { abs(it - k).let { j -> minOf(j, n - j) } }
                assertTrue("$age: stake $k on the village's bank along the stream at (${st.x}, ${st.y})", off <= 8)
            }
            // the bridge's gate on the far bank
            for (g in ring.gates.filter { it.kind == PalisadeRing.Kind.ROAD && it != ring.main }) assertTrue("$age: the bridge's gate", ring.middle(g).let { it[0] < Terrain.CLASSIC.streamX(it[1]) })
        }
    }

    /** How far ([x], [y]) is from the segment between stakes [a] and [b] (cells). */
    private fun offSegment(x: Float, y: Float, a: PalisadeRing.Stake, b: PalisadeRing.Stake): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val u = (((x - a.x) * dx + (y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hypot(x - (a.x + dx * u), y - (a.y + dy * u))
    }

    @Test fun `a way down to the stream at every age, water gates closed by stakes in the water, lintels only where the posts stand level`() {
        // where the ring runs by the stream on the village's side (the camp before the palisade can go up: it's drawn all
        // the same), one postern, between two posts, no lintel, the landing below it
        val camp = Palisade.ring(Terrain.CLASSIC, Age.OGENJ, null)
        val g = camp.gates.single { it.kind == PalisadeRing.Kind.RIVER }
        val m = camp.middle(g)
        val off = m[0] - Terrain.CLASSIC.streamX(m[1])
        assertTrue("the river gate $off cells from the stream's middle, on the village's side", off > 0.5f && off < 4.6f)
        assertTrue("a postern: two posts, no lintel, no torches' gate", !g.lintel && camp.stakes[g.a].post && camp.stakes[g.b].post)
        val (cl, ctop) = Palisade.footpath(camp)!!
        assertEquals(m[1], cl[1], 0.001f)
        assertEquals(m[0], ctop[0], 0.001f)
        for (age in ages) for ((_, ring) in rings(age)) {
            // the ring takes the stream in: no postern, the landing inside, on the village's bank, off every plot
            assertTrue("$age: no river gate", ring.river == null)
            val (l, top) = Palisade.footpath(ring)!!
            assertTrue("$age: the landing at the water's edge", abs(l[0] - Terrain.CLASSIC.streamX(l[1]) - 1.05f) < 0.001f && l[0] < top[0])
            val sx = (l[0] - l[1]) * 4f; val sy = (l[0] + l[1]) * 2f
            assertTrue("$age: the landing inside the ring", VillageLayout.screenMetric(sx, sy) < Terrain.CLASSIC.forestEdge(age, VillageLayout.angle(sx, sy)))
            for (c in Terrain.CLASSIC.centers) assertTrue("$age: the landing on a plot", abs(l[1] - c[1]) > 1.5f || top[0] + 1.2f < c[0] - 1.5f || l[0] - 0.6f > c[0] + 1.5f)
            // each water gate closed from bank to bank by shorter stakes, some standing in the water, with gaps between them
            for (w in ring.gates.filter { it.kind == PalisadeRing.Kind.WATER }) {
                assertTrue("$age: no beam over the water", !w.lintel)
                val a = ring.stakes[w.a]; val b = ring.stakes[w.b]
                val row = ring.wet.filter { offSegment(it.x, it.y, a, b) < 0.05f }.sortedBy { hypot(it.x - a.x, it.y - a.y) }
                assertTrue("$age: ${row.size} stakes across a water gate", row.size >= 3)
                assertTrue("$age: some stand in the water", row.any { abs(it.x - Terrain.CLASSIC.streamX(it.y)) < 0.95f })
                assertTrue("$age: shorter than the posts", row.all { it.h < a.h && it.h < b.h })
                for (j in 1 until row.size) {
                    val gap = hypot(row[j].x - row[j - 1].x, row[j].y - row[j - 1].y)
                    assertTrue("$age: a gap for the current between two of them ($gap cells)", gap > Palisade.WIDTH * 0.8f + 0.1f)
                }
            }
            // a lintel only over a road or path gate whose posts stand about level on the screen, as the main gate's do
            for (gate in ring.gates.filter { it.lintel }) {
                val a = ring.stakes[gate.a]; val b = ring.stakes[gate.b]
                val run = abs((b.x - b.y) - (a.x - a.y)) * 4f; val rise = abs((b.x + b.y) - (a.x + a.y)) * 2f
                assertTrue("$age: a ${gate.kind} gate's lintel rising $rise px over $run", (gate.kind == PalisadeRing.Kind.ROAD || gate.kind == PalisadeRing.Kind.PATH) && rise <= run * 0.4f)
            }
            assertTrue("$age: the main gate keeps its lintel", ring.main!!.lintel)
        }
        // the gate by the bridge stands without a lintel from the village on, its posts one behind the other
        assertTrue(Palisade.ring(Terrain.CLASSIC, Age.VAS, null).gates.filter { it.kind == PalisadeRing.Kind.ROAD && it != Palisade.ring(Terrain.CLASSIC, Age.VAS, null).main }.none { it.lintel })
    }

    @Test fun `a tap on the stepping stones down to the landing opens the stream bank, and the stakes by the water the palisade`() {
        val s = village(Age.ZASELEK, FIELD, HUT, WELL, KOZOLEC, TENT, HUT, FIELD)
        val p = s.buildings.last()
        val w = 400; val h = 180
        val r = VillageRenderer()
        r.render(PixelCanvas(w, h), s, Frame(time = 2.0, hour = 11f, month = 6))
        val comp = VillageLayout.composition(w, h, false)
        val ring = Palisade.ring(Terrain.CLASSIC, s.age, null)
        val (l, top) = Palisade.footpath(ring)!!
        // the first stone, at the top of the footpath
        val sx = top[0] + 1.2f; val sy = l[1] - 0.1f
        assertEquals(VillageHit.OnSpot("riverbank"), r.hitTest((comp.fireX + (sx - sy) * 4f).toInt(), (comp.fireY + (sx + sy) * 2f - 1f).toInt(), 2))
        // a water gate's post is still the palisade's
        val post = ring.stakes[ring.gates.first { it.kind == PalisadeRing.Kind.WATER }.a]
        assertEquals(VillageHit.OnBuilding(p), r.hitTest((comp.fireX + (post.x - post.y) * 4f).toInt(), (comp.fireY + (post.x + post.y) * 2f - post.h / 2f).toInt(), 2))
    }

    @Test fun `no stake stands in the stream, on the road or on the path`() {
        for (age in ages) for ((fg, ring) in rings(age)) for (st in ring.stakes) {
            assertTrue("$age: a stake in the stream at ${st.x}, ${st.y}", abs(st.x - Terrain.CLASSIC.streamX(st.y)) > 1.5f)
            if (st.x > 0f || age >= Age.ZASELEK) assertTrue("$age: a stake on the road at ${st.x}, ${st.y}", abs(st.y) > roadHalf(age) + 0.4f)
            if (fg != null) assertTrue("$age: a stake on the path", !fg.onPath(fg.comp.fireX + (st.x - st.y) * 4f, fg.comp.fireY + (st.x + st.y) * 2f, slack = 1f))
        }
    }

    @Test fun `a tap on the gate or on any stretch of the ring opens the palisade`() {
        val s = village(Age.ZASELEK, FIELD, HUT, WELL, KOZOLEC, TENT, HUT, FIELD)
        val p = s.buildings.last()
        val w = 240; val h = 160
        val r = VillageRenderer()
        r.render(PixelCanvas(w, h), s, Frame(time = 2.0, hour = 11f, month = 6))
        val comp = VillageLayout.composition(w, h, false)
        val ring = Palisade.ring(Terrain.CLASSIC, s.age, null)
        fun hitAt(st: PalisadeRing.Stake) = r.hitTest((comp.fireX + (st.x - st.y) * 4f).toInt(), (comp.fireY + (st.x + st.y) * 2f - st.h / 2f).toInt(), 2)
        // the main gate's posts, and the far side of the ring straight above the fire
        val main = ring.main!!
        assertEquals(VillageHit.OnBuilding(p), hitAt(ring.stakes[main.a]))
        assertEquals(VillageHit.OnBuilding(p), hitAt(ring.stakes[main.b]))
        assertEquals(VillageHit.OnBuilding(p), hitAt(ring.stakes.filter { it.x + it.y < 0f }.minBy { abs(it.x - it.y) }))
        // its rectangle is the main gate's; TalkBack's body of it lands on it
        val rect = r.hitRects().first { it.first == p }.second
        val gx = comp.fireX + (ring.gatePoint()[0] - ring.gatePoint()[1]) * 4f
        assertTrue(gx.toInt() in rect[0]..rect[2])
        val body = TownAnchors.bodies(s, w, h).toMap().getValue(p.id)
        assertEquals(VillageHit.OnBuilding(p), r.hitTest(body.x.toInt(), body.y.toInt(), 2))
    }

    @Test fun `an older save's palisade on a plot draws the same ring and leaves the plot grass`() {
        val s = village(Age.ZASELEK, FIELD, HUT, WELL, KOZOLEC, TENT, HUT, FIELD)
        val old = s.copy(buildings = s.buildings.map { if (it.type == PALISADE) it.copy(plot = 7) else it })
        val frame = Frame(time = 2.0, hour = 11f, month = 6)
        val a = PixelCanvas(240, 160).also { VillageRenderer().render(it, s, frame) }
        val b = PixelCanvas(240, 160).also { VillageRenderer().render(it, old, frame) }
        assertTrue(a.pixels.contentEquals(b.pixels))
    }

    /** PNG snapshots in build/village-snapshots (50-…) for visual review: the whole ring, and its gates up close. */
    @Test fun `render the palisade`() {
        val dir = File("build/village-snapshots").apply { mkdirs() }
        fun write(c: PixelCanvas, name: String, scale: Int) {
            val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
            ImageIO.write(img, "png", File(dir, "$name.png"))
        }
        val hamlet = village(Age.ZASELEK, FIELD, HUT, WELL, KOZOLEC, TENT, HUT, FIELD)
        val town = village(Age.VAS, FIELD, HUT, WELL, KOZOLEC, HOUSE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)
            .let { s -> s.copy(buildings = s.buildings.map { if (it.type == PALISADE) it.copy(level = 3) else it }) }
        val r = VillageRenderer()
        for ((name, s, f) in listOf(
            Triple("50-palisade-hamlet-day", hamlet, Frame(time = 3.0, hour = 11f, month = 6)),
            Triple("51-palisade-village-night", town, Frame(time = 3.0, hour = 22.5f, month = 9)),
            Triple("52-palisade-going-up", hamlet, Frame(time = 3.0, hour = 11f, month = 6, justBuilt = "p", justBuiltProgress = 0.5f)),
        )) PixelCanvas(400, 180).also { r.render(it, s, f); write(it, name, 3) }
        PixelCanvas(fit.width, fit.height).also { r.render(it, town, Frame(time = 3.0, hour = 11f, month = 6)); write(it, "53-palisade-town-1080x2400", 2) }
        PixelCanvas(fit.width, fit.height).also { r.render(it, hamlet, Frame(time = 3.0, hour = 11f, month = 6)); write(it, "53-palisade-hamlet-1080x2400", 2) }
        // up close (k = 3) at each gate of the town, at dusk: the torches are lit
        val rig = CameraRig.town(fit, 1080, 2400)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val ring = Palisade.ring(Terrain.CLASSIC, town.age, Foreground.of(town.age, fit.width, fit.height))
        for (g in ring.gates) {
            val m = ring.middle(g)
            val lens = Lens.of(3, rig.lookAt(comp.fireX + (m[0] - m[1]) * 4f, comp.fireY + (m[0] + m[1]) * 2f - 6f, rig.minScale * 3.75f), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val c = PixelCanvas(fit.width, fit.height)
            r.render(c, town, Frame(time = 3.0, hour = 20.5f, month = 6, lens = lens))
            write(c, "54-palisade-gate-${g.kind.name.lowercase()}-${ring.gates.indexOf(g)}-k3", 2)
        }
        // the hamlet's corner by the stream up close (k = 2) by day: the far bank all along, the bridge's gate, the water gates
        val hr = Palisade.ring(Terrain.CLASSIC, hamlet.age, Foreground.of(hamlet.age, fit.width, fit.height))
        for (g in hr.gates.filter { it.kind == PalisadeRing.Kind.WATER || it.kind == PalisadeRing.Kind.ROAD && it != hr.main }) {
            val m = hr.middle(g)
            val lens = Lens.of(2, rig.lookAt(comp.fireX + (m[0] - m[1]) * 4f, comp.fireY + (m[0] + m[1]) * 2f - 6f, rig.minScale * 2f), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val c = PixelCanvas(fit.width, fit.height)
            r.render(c, hamlet, Frame(time = 3.0, hour = 11f, month = 6, lens = lens))
            write(c, "55-palisade-hamlet-stream-${g.kind.name.lowercase()}-${hr.gates.indexOf(g)}-k2", 2)
        }
    }

    @Test fun `the palisade's bubble and its visitor are at the main gate`() {
        val s = village(Age.VAS, FIELD, HUT, WELL, KOZOLEC, TENT, HUT, FIELD)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val g = TownAnchors.gate(s)
        val gx = comp.fireX + (g[0] - g[1]) * 4f; val gy = comp.fireY + (g[0] + g[1]) * 2f
        val top = TownAnchors.of(s, fit.width, fit.height).getValue(TownPlace.At(PALISADE))
        assertEquals(gx, top.x, 0.5f)
        assertTrue("the bubble at $top over the gate at $gx, $gy", top.y < gy - g[2] && top.y > gy - g[2] - 6f)
        // someone waiting at the palisade stands just inside the gate
        val v = TownAnchors.visitorSpot(s, TownPlace.At(PALISADE), comp, fit.width, fit.height)!!
        val vx = (v[0] - v[1]) * 4f; val vy = (v[0] + v[1]) * 2f
        val m = VillageLayout.screenMetric(vx, vy)
        assertTrue("inside the ring: $m", m < Terrain.CLASSIC.forestEdge(s.age, VillageLayout.angle(vx, vy)) - Palisade.INSET - 2f)
        assertTrue("near the gate", hypot(v[0] - g[0], v[1] - g[1]) < 3f)
        // an older save, its palisade still on a plot, has its anchors at the gate all the same
        val old = s.copy(buildings = s.buildings.map { if (it.type == PALISADE) it.copy(plot = 7) else it })
        assertEquals(top, TownAnchors.of(old, fit.width, fit.height).getValue(TownPlace.At(PALISADE)))
    }
}
