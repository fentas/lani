package si.lanisce.lani.game.render

import org.junit.Assume.assumeTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.villagers.Villager

/**
 * What a frame of the full-screen town costs per pass, on Teo's tablet's canvas (2560 × 1600: 320 × 200 at 8 ×), at rest,
 * closer, closest, panning and pinching (LANI_PROFILE=1; not part of the suite's checks).
 */
class TownFrameProfileTest {
    private val teo = Founding("friuli", Landscape.COAST, 551323397L).village(1_000L).copy(morale = 70, fire = 70)

    private fun grown(s: GameState, age: Age): GameState {
        val n = PLOTS_PER_AGE[age.ordinal]
        val types = if (age.ordinal <= Age.TABOR.ordinal) listOf(BuildingType.TENT, BuildingType.TENT)
        else List(n) { i -> listOf(BuildingType.HUT, BuildingType.FIELD, BuildingType.HOUSE, BuildingType.WELL, BuildingType.PALISADE, BuildingType.LIPA, BuildingType.KOZOLEC)[i % 7] }
        return s.copy(age = age, villagers = 4 + n, morale = 70, fire = 70, buildings = types.take(n).mapIndexed { i, t -> Building("b$i", t, if (t.onPlot) i else NO_PLOT) })
    }

    private val cast = listOf(
        Villager(id = "micka", name = "Babica Micka", emoji = "👵", art = "grandma", home = listOf("house", "hut")),
        Villager(id = "janez", name = "Stari Janez", emoji = "👴", art = "grandpa", home = listOf("lipa", "spot:road")),
        Villager(id = "luka", name = "Pastir Luka", emoji = "🐑", art = "shepherd", home = listOf("spot:meadow")),
        Villager(id = "tone", name = "Kovač Tone", emoji = "⚒️", art = "smith", home = listOf("spot:woodpile")),
        Villager(id = "ancka", name = "Teta Ančka", emoji = "📻", art = "aunt", home = listOf("spot:pond")),
    )

    private val passes = listOf("backdrop", "ground", "gather", "objects", "outline", "fx", "light")

    private class Acc { val all = ArrayList<Double>(); val pass = HashMap<String, ArrayList<Double>>() }

    private fun med(xs: List<Double>) = xs.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }

    /** A poor man's sampling profiler: [thread]'s stack every ~0.3 ms, counted by method (inclusive, once per sample) and by its top frame in the app. */
    private class Sampler(private val thread: Thread) {
        val inclusive = HashMap<String, Int>()
        val self = HashMap<String, Int>()
        var samples = 0
        @Volatile var on = false
        @Volatile var stop = false
        private val t = Thread {
            while (!stop) {
                if (on) {
                    val st = thread.stackTrace
                    val inRender = st.any { it.className.endsWith("VillageRenderer") && it.methodName == "render" }
                    if (inRender) {
                        samples++
                        val seen = HashSet<String>()
                        var top: String? = null
                        for (f in st) {
                            if (!f.className.startsWith("si.lanisce.lani")) continue
                            val name = f.className.substringAfterLast('.') + "." + f.methodName
                            if (top == null) top = name
                            if (seen.add(name)) inclusive[name] = (inclusive[name] ?: 0) + 1
                        }
                        if (top != null) self[top] = (self[top] ?: 0) + 1
                    }
                }
                java.util.concurrent.locks.LockSupport.parkNanos(300_000)
            }
        }.apply { isDaemon = true; start() }
        fun report(title: String) {
            println("== $title: $samples samples")
            println("  inclusive: " + inclusive.entries.sortedByDescending { it.value }.take(28).joinToString(", ") { "${it.key} ${100 * it.value / maxOf(1, samples)}%" })
            println("  self: " + self.entries.sortedByDescending { it.value }.take(22).joinToString(", ") { "${it.key} ${100 * it.value / maxOf(1, samples)}%" })
            inclusive.clear(); self.clear(); samples = 0
        }
    }

    /** Where a frame's time goes, by method, at rest closer up and panning (LANI_PROFILE=1). */
    @Test fun sampled() {
        assumeTrue(System.getenv("LANI_PROFILE") != null)
        val vw = 2560; val vh = 1600
        val fit = SceneFit.town(vw, vh)
        val rig = CameraRig.town(fit, vw, vh)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val sampler = Sampler(Thread.currentThread())
        for ((name, s) in listOf("teo-vas" to grown(teo, Age.VAS), "classic-vas" to grown(teo.copy(land = Land.CLASSIC), Age.VAS))) for (hour in listOf(11f, 22f)) {
            fun cam(scale: Float, dx: Float = 0f) = rig.clamp(Camera(scale, vw / 2f - (comp.fireX + dx) * scale, vh / 2f - (comp.fireY - 10f) * scale))
            fun lensOf(c: Camera): Lens { val k = rig.detail(c.scale); return if (k == 1) Lens() else Lens.of(k, c, vw, vh, fit.width, fit.height, fit.width, fit.height) }
            for ((sc, lensAt) in listOf<Pair<String, (Int) -> Lens>>(
                "rest k1" to { _ -> Lens() },
                "rest k3" to { _ -> lensOf(cam(rig.minScale * 3f)) },
                "pan k3" to { i -> lensOf(cam(rig.minScale * 3f, (i % 40 - 20) * 1.5f)) },
            )) {
                val r = VillageRenderer()
                val c = PixelCanvas(fit.width, fit.height)
                val frame = Frame(hour = hour, month = 8, people = cast, today = "2026-08-20", sea = true)
                repeat(20) { r.render(c, s, frame.copy(time = it / 12.0, lens = lensAt(it))) }
                sampler.on = true
                for (i in 0 until 120) r.render(c, s, frame.copy(time = 2 + i / 12.0, lens = lensAt(i + 20)))
                sampler.on = false
                sampler.report("$name ${hour}h $sc")
            }
        }
        sampler.stop = true
    }

    @Test fun profile() {
        assumeTrue(System.getenv("LANI_PROFILE") != null)
        val vw = 2560; val vh = 1600
        val fit = SceneFit.town(vw, vh)
        val rig = CameraRig.town(fit, vw, vh)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val states = listOf("teo-camp" to teo, "teo-vas" to grown(teo, Age.VAS), "classic-vas" to grown(teo.copy(land = Land.CLASSIC), Age.VAS))
        println("canvas ${fit.width}x${fit.height} at ${fit.scale}x, rig ${rig.minScale}..${rig.maxScale}")
        for ((name, s) in states) for (hour in listOf(11f, 22f)) {
            val at = comp.fireX.toFloat(); val aty = comp.fireY - 10f
            fun cam(scale: Float, dx: Float = 0f) = rig.clamp(Camera(scale, vw / 2f - (at + dx) * scale, vh / 2f - aty * scale))
            fun lensOf(c: Camera): Lens { val k = rig.detail(c.scale); return if (k == 1) Lens() else Lens.of(k, c, vw, vh, fit.width, fit.height, fit.width, fit.height) }
            val scenarios = listOf<Pair<String, (Int) -> Lens>>(
                "rest k1" to { _ -> Lens() },
                "rest k2 (1.5x)" to { _ -> lensOf(cam(rig.crisp(rig.minScale * 1.5f))) },
                "rest k2 (2x)" to { _ -> lensOf(cam(rig.minScale * 2f)) },
                "rest k3 (2.5x)" to { _ -> lensOf(cam(rig.crisp(rig.minScale * 2.5f))) },
                "rest k3 (3x)" to { _ -> lensOf(cam(rig.minScale * 3f)) },
                "pan k2" to { i -> lensOf(cam(rig.minScale * 2f, (i % 40 - 20) * 1.5f)) },
                "pan k3" to { i -> lensOf(cam(rig.minScale * 3f, (i % 40 - 20) * 1.5f)) },
                "pinch 1..3x" to { i -> lensOf(cam(rig.minScale * (1f + (i % 40) / 20f))) },
            )
            // as the town draws its closer look since 2026-10-27: a window of the picture of no offset (TownFrames)
            val windowed = listOf<Pair<String, (Int) -> Camera>>(
                "win rest k2 (1.5x)" to { _ -> cam(rig.crisp(rig.minScale * 1.5f)) },
                "win rest k2 (2x)" to { _ -> cam(rig.minScale * 2f) },
                "win rest k3 (2.5x)" to { _ -> cam(rig.crisp(rig.minScale * 2.5f)) },
                "win rest k3 (3x)" to { _ -> cam(rig.minScale * 3f) },
                "win pan k2" to { i -> cam(rig.minScale * 2f, (i % 40 - 20) * 1.5f) },
                "win pan k3" to { i -> cam(rig.minScale * 3f, (i % 40 - 20) * 1.5f) },
                "win pinch k2" to { i -> cam(rig.minScale * (1.5f + (i % 20) / 40f)) },
                "win pinch k3" to { i -> cam(rig.minScale * (2.5f + (i % 20) / 20f)) },
            )
            // a canvas of each size, as the town keeps them (its drawing state with it)
            val pool = HashMap<Long, PixelCanvas>()
            fun canvas(w: Int, h: Int) = pool.getOrPut((w.toLong() shl 32) or h.toLong()) { PixelCanvas(w, h) }
            val all = scenarios.map { (sc, lensAt) -> sc to { i: Int -> canvas(fit.width, fit.height) to lensAt(i) } } +
                windowed.map { (sc, camAt) ->
                    sc to { i: Int ->
                        val c = camAt(i); val k = rig.detail(c.scale)
                        val scr = TownFrames.screen(k, c, vw, vh, fit.width, fit.height)
                        val w = TownFrames.window(k, scr, sc.contains("pan") || sc.contains("pinch"), fit.width, fit.height)
                        canvas(w.w, w.h).apply { originX = w.x0; originY = w.y0 } to TownFrames.lens(k, fit.width, fit.height)
                    }
                }
            for ((sc, at) in all) {
                val r = VillageRenderer()
                val frame = Frame(hour = hour, month = 8, people = cast, today = "2026-08-20", sea = true)
                repeat(20) { val (c, l) = at(it); r.render(c, s, frame.copy(time = it / 12.0, lens = l)) }
                r.profile = true
                val acc = Acc()
                for (i in 0 until 40) {
                    val (c, l) = at(i + 20)
                    val t0 = System.nanoTime()
                    r.render(c, s, frame.copy(time = 2 + i / 12.0, lens = l))
                    acc.all += (System.nanoTime() - t0) / 1e6
                    for (part in r.lastProfile.split(" ").chunked(2)) if (part.size == 2) acc.pass.getOrPut(part[0]) { ArrayList() } += part[1].replace(',', '.').toDouble()
                }
                println("%-12s %5.1fh %-16s total %6.2f ms | %s".format(name, hour, sc, med(acc.all), passes.joinToString(" ") { "%s %.2f".format(it, med(acc.pass[it].orEmpty())) }))
            }
        }
    }
}
