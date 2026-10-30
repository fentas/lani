package si.lanisce.lani.game.render

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.villagers.Villager
import java.util.concurrent.atomic.AtomicReference

/**
 * The town's closer look as a window of its picture (docs/plans/05-built/zoom-frames.md; [TownFrames], [TownPictures]):
 * a canvas holding a window of the picture at detail 2 or 3 shows what the whole picture has there, pixel for pixel,
 * wherever the window lies and whatever its size (but its [TownFrames.apron]); the ground kept in cells of the picture
 * ([GroundCache]) gives the same frames as the ground drawn whole while the window pans and zooms, the day turns, it
 * rains, the village builds; a pan costs about what a frame at rest does; the cells stay under their memory's cap; and
 * the renderer can be read (its anchors, its animals, its taps) while it draws on another thread.
 */
class TownWindowTest {
    private val teo = Founding("friuli", Landscape.COAST, 551323397L).village(1_000L).copy(morale = 70, fire = 70)

    @After fun kept() { VillageRenderer.groundKept = true; Stamps.on = true }

    private fun grown(s: GameState, age: Age): GameState {
        val n = PLOTS_PER_AGE[age.ordinal]
        val types = if (age.ordinal <= Age.TABOR.ordinal) listOf(BuildingType.TENT, BuildingType.TENT)
        else List(n) { i -> listOf(BuildingType.HUT, BuildingType.FIELD, BuildingType.HOUSE, BuildingType.WELL, BuildingType.PALISADE, BuildingType.LIPA)[i % 6] }
        return s.copy(age = age, villagers = 4 + n, morale = 70, fire = 70, buildings = types.take(n).mapIndexed { i, t -> Building("b$i", t, if (t.onPlot) i else NO_PLOT) })
    }

    private val cast = listOf(
        Villager(id = "micka", name = "Babica Micka", emoji = "👵", art = "grandma", home = listOf("house", "hut")),
        Villager(id = "luka", name = "Pastir Luka", emoji = "🐑", art = "shepherd", home = listOf("spot:meadow")),
        Villager(id = "tone", name = "Kovač Tone", emoji = "⚒️", art = "smith", home = listOf("spot:woodpile")),
        Villager(id = "ancka", name = "Teta Ančka", emoji = "📻", art = "aunt", home = listOf("spot:pond")),
    )

    /** [s] with a hut more, on the first free plot (its path to the road: the ground's paths change). */
    private fun oneMore(s: GameState): GameState =
        s.copy(buildings = s.buildings + Building("new", BuildingType.HUT, (0 until VillageLayout.MAX_PLOTS).first { p -> s.buildings.none { it.plot == p } }))

    /** [a]'s pixels inside [r] (lens canvas coordinates) against [b]'s there; the first one that differs, or null. */
    private fun differs(a: PixelCanvas, b: PixelCanvas, r: PixelRect): String? {
        for (y in r.y0 until r.y1) for (x in r.x0 until r.x1) {
            if (!a.inside(x, y) || !b.inside(x, y)) continue
            val pa = a.pixels[a.index(x, y)]; val pb = b.pixels[b.index(x, y)]
            if (pa != pb) return "($x, $y): %08x, the whole picture's %08x".format(pa, pb)
        }
        return null
    }

    /** The windows of a [pw] × [ph] picture to check: corners, the middle, small and big, at odd places. */
    private fun windows(pw: Int, ph: Int): List<PixelRect> = listOf(
        PixelRect(0, 0, 160, 112), PixelRect(pw - 208, ph - 144, 208, 144), PixelRect(pw / 2 - 157, ph / 2 - 101, 333, 227),
        PixelRect(37, ph / 3, 256, 96), PixelRect(pw / 3, 13, 144, ph / 2),
    ).map { PixelRect(it.x0.coerceIn(0, pw - 16), it.y0.coerceIn(0, ph - 16), it.w.coerceAtMost(pw), it.h.coerceAtMost(ph)) }

    @Test fun `a window of the closer picture is that part of the whole picture`() {
        var n = 0
        for ((vw, vh) in listOf(2560 to 1600, 1080 to 2400)) {
            val fit = SceneFit.town(vw, vh)
            for (k in 2..3) {
                val lens = TownFrames.lens(k, fit.width, fit.height)
                val pw = fit.width * k; val ph = fit.height * k
                val storm = grown(teo, Age.VAS).copy(event = GameEvent("e", EventKind.STORM, 3, 0, 0))
                for ((name, s, f) in listOf(
                    Triple("coast camp, noon", teo, Frame(time = 3.0, hour = 12f, month = 8, people = cast, today = "2026-08-20", sea = true)),
                    Triple("coast village, night", grown(teo, Age.VAS), Frame(time = 4.2, hour = 22.5f, month = 8, people = cast, today = "2026-08-20", sea = true)),
                    Triple("coast in the storm", storm, Frame(time = 5.1, hour = 15f, month = 8, today = "2026-08-20", sea = true, calm = true)),
                    Triple("classic, autumn dusk", grown(teo.copy(land = Land.CLASSIC), Age.TRG), Frame(time = 6.3, hour = 19.4f, month = 10, people = cast, today = "2026-10-12")),
                    Triple("lake in the snow", grown(teo.copy(land = Land.of(Landscape.LAKE, 42L)), Age.ZASELEK), Frame(time = 7.7, hour = 9f, month = 1, today = "2026-01-12")),
                )) {
                    for (w in windows(pw, ph)) {
                        // the screen inside the window by its apron, as the town asks for it (TownFrames.window): what is
                        // drawn over the screen (the rain, its splashes, the leaves) falls the same in the whole picture
                        val a = TownFrames.apron(k)
                        val inner = PixelRect(w.x0 + a, w.y0 + a, w.w - 2 * a, w.h - 2 * a)
                        val frame = f.copy(lens = lens, screen = inner)
                        val whole = PixelCanvas(pw, ph)
                        VillageRenderer().render(whole, s, frame)
                        val c = PixelCanvas(w.w, w.h).apply { originX = w.x0; originY = w.y0 }
                        VillageRenderer().render(c, s, frame)
                        val d = differs(c, whole, inner)
                        assertEquals("$name ${fit.width}x${fit.height} k=$k window $w: $d", null, d)
                        n++
                    }
                }
            }
        }
        println("$n windows the same as their whole pictures")
    }

    @Test fun `the window covers the screen and its apron, inside the picture`() {
        for ((vw, vh) in listOf(2560 to 1600, 1080 to 2400, 2400 to 1080)) {
            val fit = SceneFit.town(vw, vh)
            val rig = CameraRig.town(fit, vw, vh)
            for (scale in listOf(rig.minScale * 1.5f, rig.minScale * 1.7f, rig.minScale * 2f, rig.minScale * 2.5f, rig.minScale * 3.3f, rig.maxScale.toFloat())) {
                val k = rig.detail(scale)
                if (k < 2) continue
                for (fx in listOf(0f, 0.3f, 0.5f, 0.97f, 1f)) for (fy in listOf(0f, 0.5f, 1f)) {
                    val c = rig.lookAt(fit.width * fx, fit.height * fy, scale)
                    val s = TownFrames.screen(k, c, vw, vh, fit.width, fit.height)
                    for (moving in listOf(false, true)) {
                        val w = TownFrames.window(k, s, moving, fit.width, fit.height)
                        assertTrue("$w covers ${TownFrames.needed(k, s, fit.width, fit.height)}", w.covers(TownFrames.needed(k, s, fit.width, fit.height)))
                        assertTrue("$w inside the picture", w.x0 >= 0 && w.y0 >= 0 && w.x1 <= fit.width * k && w.y1 <= fit.height * k)
                        assertTrue("$w size", w.w % TownFrames.SIZE_STEP == 0 || w.w == fit.width * k)
                        // a few pixels more than the screen, not a picture's worth
                        val more = 2 * (TownFrames.apron(k) + if (moving) TownFrames.margin(k) else 0) + TownFrames.SIZE_STEP
                        assertTrue("$w for $s", w.w <= s.w + more && w.h <= s.h + more)
                    }
                }
            }
        }
    }

    /** A camera's path at detail [k] over the tablet's picture: a pan back and forth, a pinch in and out within the level. */
    private fun path(k: Int, vw: Int, vh: Int): List<Camera> {
        val fit = SceneFit.town(vw, vh)
        val rig = CameraRig.town(fit, vw, vh)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val base = rig.minScale * (k + 0.2f)
        val out = ArrayList<Camera>()
        for (i in 0 until 18) out += rig.lookAt(comp.fireX - 60f + i * 7f, comp.fireY - 10f + (i % 5) * 3f, base)
        for (i in 0 until 10) out += rig.clamp(Camera(base * (1f + i * 0.03f), vw / 2f - comp.fireX * base * (1f + i * 0.03f), vh / 2f - comp.fireY * base * (1f + i * 0.03f)))
        return out
    }

    @Test fun `the ground kept in cells draws the same frames as the ground drawn whole while the camera moves`() {
        var frames = 0
        val vw = 2560; val vh = 1600
        val fit = SceneFit.town(vw, vh)
        for (land in listOf(Land.of(Landscape.COAST, 551323397L), Land.of(Landscape.LAKE, 42L), Land.of(Landscape.MOUNTAINS, 3L), Land.CLASSIC)) {
            val s = grown(teo.copy(land = land), Age.VAS)
            val kept = VillageRenderer(); val whole = VillageRenderer()
            var t = 2.0
            for (k in listOf(2, 3, 2)) for ((i, cam) in path(k, vw, vh).withIndex()) {
                val screen = TownFrames.screen(k, cam, vw, vh, fit.width, fit.height)
                val w = TownFrames.window(k, screen, i % 3 != 0, fit.width, fit.height)
                // the day turning, a building more, the rain: what the ground's key holds
                val state = if (i > 20) oneMore(s) else s
                val frame = Frame(time = t, hour = if (i < 12) 12f else 19.6f + i * 0.02f, month = if (i == 25) 11 else 8, people = cast, lens = TownFrames.lens(k, fit.width, fit.height), screen = screen, sea = true, calm = true, today = "2026-08-20")
                t += 0.083
                val a = PixelCanvas(w.w, w.h).apply { originX = w.x0; originY = w.y0 }
                val b = PixelCanvas(w.w, w.h).apply { originX = w.x0; originY = w.y0 }
                VillageRenderer.groundKept = true
                kept.render(a, state, frame)
                VillageRenderer.groundKept = false
                whole.render(b, state, frame)
                assertEquals("${land.kind}:${land.seed} k=$k step $i window $w", null, differs(a, b, w))
                frames++
            }
        }
        println("$frames frames the same")
    }

    @Test fun `the trees' stamps draw the same frames as the trees painted`() {
        var frames = 0
        for ((vw, vh) in listOf(2560 to 1600, 1080 to 2400)) {
            val fit = SceneFit.town(vw, vh)
            val rig = CameraRig.town(fit, vw, vh)
            for (land in listOf(Land.of(Landscape.COAST, 551323397L), Land.of(Landscape.HILLS, 7L), Land.CLASSIC)) {
                val s = grown(teo.copy(land = land), Age.TRG)
                val stamped = VillageRenderer(); val painted = VillageRenderer()
                var i = 0
                // every season (the crowns' colours, the snow, June's linden, November's bare beeches), at every detail, panning
                for (month in listOf(4, 6, 9, 11, 1)) for (k in 1..3) for (step in 0 until 3) {
                    val f = Frame(time = 2.0 + i * 0.083, hour = 11f, month = month, people = cast, sea = true, calm = true, today = "2026-%02d-12".format(month))
                    val (c1, c2, frame) = if (k == 1) Triple(PixelCanvas(fit.width, fit.height), PixelCanvas(fit.width, fit.height), f)
                    else {
                        val cam = rig.lookAt(fit.width * (0.3f + step * 0.2f), fit.height * 0.6f, rig.minScale * (k + 0.2f))
                        val screen = TownFrames.screen(k, cam, vw, vh, fit.width, fit.height)
                        val w = TownFrames.window(k, screen, step > 0, fit.width, fit.height)
                        Triple(PixelCanvas(w.w, w.h).apply { originX = w.x0; originY = w.y0 }, PixelCanvas(w.w, w.h).apply { originX = w.x0; originY = w.y0 },
                            f.copy(lens = TownFrames.lens(k, fit.width, fit.height), screen = screen))
                    }
                    Stamps.on = true
                    stamped.render(c1, s, frame)
                    Stamps.on = false
                    painted.render(c2, s, frame)
                    assertTrue("${land.kind} month $month k=$k step $step", c1.pixels.contentEquals(c2.pixels))
                    i++; frames++
                }
                assertTrue("stamps kept: ${stamped.stamps.size}", stamped.stamps.size > 50)
            }
        }
        println("$frames frames the same with the trees stamped")
    }

    @Test fun `a pan draws only the ground newly in view, a building or a new month all of it`() {
        val vw = 2560; val vh = 1600
        val fit = SceneFit.town(vw, vh)
        val s = grown(teo, Age.VAS)
        val r = VillageRenderer()
        val cams = path(3, vw, vh)
        var redrawn = 0; var cells = 0
        fun frameAt(cam: Camera, state: GameState = s, month: Int = 8): Int {
            val screen = TownFrames.screen(3, cam, vw, vh, fit.width, fit.height)
            val w = TownFrames.window(3, screen, false, fit.width, fit.height)
            val c = PixelCanvas(w.w, w.h).apply { originX = w.x0; originY = w.y0 }
            r.render(c, state, Frame(time = 2.0, hour = 12f, month = month, lens = TownFrames.lens(3, fit.width, fit.height), screen = screen, sea = true))
            return r.groundCells.drawn
        }
        frameAt(cams[0])
        val first = r.groundCells.size
        for (cam in cams.drop(1).take(17)) { redrawn += frameAt(cam); cells = r.groundCells.size }
        // the pan moved over 120 nominal px: the cells newly in view were drawn, each once
        assertTrue("$redrawn cells drawn in the pan, $first at first, $cells kept", redrawn in 1..(cells - first))
        // back where it started: nothing to draw
        assertEquals(0, frameAt(cams[0]))
        // a building placed (its path to the road): the ground is drawn anew; so is a new month's (the season's colours)
        assertTrue(frameAt(cams[0], oneMore(s)) > 0)
        assertTrue(frameAt(cams[0], month = 11) > 0)
        // an upgrade doesn't touch the ground
        frameAt(cams[0])
        assertEquals(0, frameAt(cams[0], s.copy(buildings = s.buildings.map { if (it.id == "b0") it.copy(level = 2) else it })))
    }

    @Test fun `the cells keep under their memory's cap, the oldest going first, and still draw the same`() {
        val vw = 1080; val vh = 2400
        val fit = SceneFit.town(vw, vh)
        val s = grown(teo, Age.VAS)
        val cap = 2L shl 20
        val kept = VillageRenderer().apply { groundCells.capBytes = cap }
        val whole = VillageRenderer()
        val rig = CameraRig.town(fit, vw, vh)
        var most = 0L
        for (i in 0 until 40) {
            val cam = rig.lookAt(fit.width * (i % 8) / 7f, fit.height * (i / 8) / 4f, rig.minScale * 3f)
            val screen = TownFrames.screen(3, cam, vw, vh, fit.width, fit.height)
            val w = TownFrames.window(3, screen, false, fit.width, fit.height)
            val frame = Frame(time = 2.0 + i * 0.08, hour = 12f, month = 8, lens = TownFrames.lens(3, fit.width, fit.height), screen = screen, sea = true)
            val a = PixelCanvas(w.w, w.h).apply { originX = w.x0; originY = w.y0 }
            val b = PixelCanvas(w.w, w.h).apply { originX = w.x0; originY = w.y0 }
            VillageRenderer.groundKept = true
            kept.render(a, s, frame)
            most = maxOf(most, kept.groundCells.bytes)
            VillageRenderer.groundKept = false
            whole.render(b, s, frame)
            assertEquals("step $i", null, differs(a, b, w))
        }
        assertTrue("${kept.groundCells.bytes} bytes kept, at most $most, the cap $cap", most <= cap)
        // the default cap holds every cell of a phone's closest look: about 13 MB of the 24
        VillageRenderer.groundKept = true
        val all = VillageRenderer()
        for (y in 0 until fit.height * 3 step 400) for (x in 0 until fit.width * 3 step 300) {
            val c = PixelCanvas(300, 400).apply { originX = x; originY = y }
            all.render(c, s, Frame(time = 2.0, hour = 12f, month = 8, lens = TownFrames.lens(3, fit.width, fit.height), sea = true))
        }
        println("a phone's closest look, every cell: %.1f MB in %d cells".format(all.groundCells.bytes / 1048576.0, all.groundCells.size))
        val cells = (fit.width * 3 + GroundCache.CELL - 1) / GroundCache.CELL * ((fit.height * 3 + GroundCache.CELL - 1) / GroundCache.CELL)
        assertTrue("${all.groundCells.size} cells of $cells", all.groundCells.size > cells / 2)
        assertTrue(all.groundCells.bytes <= GroundCache.DEFAULT_CAP)
    }

    /**
     * Teo's tablet's canvas closer up: a pan's frame (the camera moving every frame) costs about what one at rest does, now
     * that only the ground newly in view is drawn (it cost up to 5 times as much while the ground was the canvas's).
     */
    @Test fun `a pan closer up costs about what a frame at rest does`() {
        val vw = 2560; val vh = 1600
        val fit = SceneFit.town(vw, vh)
        for ((name, s) in listOf("coast-vas" to grown(teo, Age.VAS), "classic-vas" to grown(teo.copy(land = Land.CLASSIC), Age.VAS))) for (k in 2..3) {
            val cams = path(k, vw, vh).take(18)
            fun median(pan: Boolean): Double {
                val r = VillageRenderer()
                fun at(i: Int): Pair<PixelCanvas, Frame> {
                    val cam = if (pan) cams[i % cams.size] else cams[0]
                    val screen = TownFrames.screen(k, cam, vw, vh, fit.width, fit.height)
                    val w = TownFrames.window(k, screen, pan, fit.width, fit.height)
                    return PixelCanvas(w.w, w.h).apply { originX = w.x0; originY = w.y0 } to
                        Frame(time = 2 + i / 12.0, hour = 22f, month = 9, lens = TownFrames.lens(k, fit.width, fit.height), screen = screen, sea = true, people = cast)
                }
                // warmed up over the whole path (its cells drawn once), then timed, each frame in this thread's CPU time (FrameBudget)
                repeat(cams.size) { val (c, f) = at(it); r.render(c, s, f) }
                val ms = DoubleArray(31) { i ->
                    val (c, f) = at(i)
                    FrameBudget.cpuMs { r.render(c, s, f) }
                }
                return ms.sorted()[ms.size / 2]
            }
            val rest = median(false); val pan = median(true)
            println("%s %dx%d k=%d: %.2f ms a frame at rest, %.2f panning".format(name, fit.width, fit.height, k, rest, pan))
            assertTrue("$name k=$k: pan $pan ms, rest $rest ms", pan < rest * 1.6 + FrameBudget.scaled(1.0))
        }
    }

    @Test fun `the renderer can be read while it draws on another thread`() {
        val vw = 2560; val vh = 1600
        val fit = SceneFit.town(vw, vh)
        val s = grown(teo, Age.VAS)
        val r = VillageRenderer()
        val failed = AtomicReference<Throwable?>(null)
        val drawing = Thread {
            try {
                for (i in 0 until 60) {
                    val k = 1 + i % 3
                    val c = if (k == 1) PixelCanvas(fit.width, fit.height) else PixelCanvas(400, 256).apply { originX = 100 * k; originY = 60 * k }
                    r.render(c, s, Frame(time = i / 12.0, hour = 11f, month = 8, people = cast, lens = if (k == 1) Lens() else TownFrames.lens(k, fit.width, fit.height), sea = true))
                }
            } catch (e: Throwable) { failed.set(e) }
        }
        drawing.start()
        var reads = 0
        while (drawing.isAlive) {
            // what the main thread reads every frame (the bubbles, TalkBack's nodes), and a tap
            for ((_, a) in r.villagerAnchors) assertTrue(a.x.isFinite())
            for (w in r.wildlife) assertTrue(w.at.y.isFinite())
            r.lens.k
            r.hitTest(r.lens.cx(fit.width / 2f).toInt(), r.lens.cy(fit.height / 2f).toInt(), 4)
            reads++
        }
        drawing.join()
        failed.get()?.let { throw AssertionError("the drawing thread failed", it) }
        assertTrue(reads > 10)
        assertTrue(r.villagerAnchors.isNotEmpty())
    }
}
