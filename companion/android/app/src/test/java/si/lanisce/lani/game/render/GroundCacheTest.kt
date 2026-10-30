package si.lanisce.lani.game.render

import org.junit.After
import org.junit.Assert.assertArrayEquals
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

/**
 * The ground's still picture kept from frame to frame ([GroundCache]; docs/plans/05-built/coast-horizon.md): the frames are
 * the same pixels as when every frame draws the ground whole, on every land (the sea, a lake, the torrent, the rises and
 * the vineyards, the classic valley), on the town's canvases at every detail, while the water moves, the camera pans, the
 * day turns to dusk, it rains, snows, the village builds and grows; and they cost less.
 */
class GroundCacheTest {
    private val teo = Founding("friuli", Landscape.COAST, 551323397L).village(1_000L).copy(morale = 70, fire = 70)

    @After fun kept() { VillageRenderer.groundKept = true }

    private fun grown(s: GameState, age: Age): GameState {
        val n = PLOTS_PER_AGE[age.ordinal]
        val types = if (age.ordinal <= Age.TABOR.ordinal) listOf(BuildingType.TENT, BuildingType.TENT)
        else List(n) { i -> listOf(BuildingType.HUT, BuildingType.FIELD, BuildingType.HOUSE, BuildingType.WELL, BuildingType.PALISADE)[i % 5] }
        return s.copy(age = age, villagers = 4 + n, morale = 70, fire = 70, buildings = types.take(n).mapIndexed { i, t -> Building("b$i", t, if (t.onPlot) i else NO_PLOT) })
    }

    private val lands = listOf(
        Land.of(Landscape.COAST, 551323397L), Land.of(Landscape.COAST, 42L), Land.of(Landscape.LAKE, 42L), Land.of(Landscape.MOUNTAINS, 3L),
        Land.of(Landscape.HILLS, 7L), Land.of(Landscape.VALLEY, 777L), Land.CLASSIC,
    )

    /** One picture of a sequence: what is drawn, how, and through which lens (the camera's, on a [vw] × [vh] screen). */
    private class Step(val state: GameState, val frame: Frame)

    /** A sequence over [s] on a [vw] × [vh] screen: the water moving, panning and zooming, the light changing, the weather, a building more. */
    private fun steps(s: GameState, vw: Int, vh: Int): List<Step> {
        val fit = SceneFit.town(vw, vh)
        val rig = CameraRig.town(fit, vw, vh)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val t = Terrain.of(s)
        val at = if (t.sea != null) comp.fireX + t.shoreX(-30f) else comp.fireX - 40f
        fun lens(k: Int, dx: Float) = Lens.of(k, rig.lookAt(at + dx, comp.horizon + 30f, (rig.minScale * k).toFloat()), vw, vh, fit.width, fit.height, fit.width, fit.height)
        val out = ArrayList<Step>()
        // the water moving at noon, at every detail, the camera panning a little at the closer looks
        for (k in 1..3) for (i in 0 until 5) out += Step(s, Frame(time = 2.0 + i * 0.37, hour = 12f, month = 8, lens = if (k == 1) Lens() else lens(k, i * 3f), sea = true, calm = true))
        // the evening coming on (the sky's colour at the horizon changing frame by frame), at night
        for (i in 0 until 6) out += Step(s, Frame(time = 5.0 + i * 0.4, hour = 19.4f + i * 0.08f, month = 8, sea = true, calm = true))
        out += Step(s, Frame(time = 9.0, hour = 22.5f, month = 8, lens = lens(2, 0f), sea = true, calm = true))
        // the storm's rain (puddles catching the grey sky), the winter's ice and snow
        val storm = s.copy(event = GameEvent("e", EventKind.STORM, 3, 0, 0))
        for (i in 0 until 3) out += Step(storm, Frame(time = 11.0 + i * 0.4, hour = 14f, month = 8, sea = true, calm = true))
        for (i in 0 until 3) out += Step(s, Frame(time = 13.0 + i * 0.4, hour = 11f, month = 1, lens = if (i == 2) lens(3, 0f) else Lens(), sea = true, calm = true))
        // a building more (its path to the road), then the next age
        val more = s.copy(buildings = s.buildings + Building("new", BuildingType.HUT, s.buildings.count { it.type.onPlot }))
        for (i in 0 until 2) out += Step(more, Frame(time = 15.0 + i * 0.4, hour = 12f, month = 8, sea = true, calm = true))
        val next = Age.entries.getOrNull(s.age.ordinal + 1)
        if (next != null) out += Step(grown(s, next), Frame(time = 16.0, hour = 12f, month = 8, sea = true, calm = true))
        return out
    }

    @Test fun `the kept ground draws the same frames as the ground drawn whole`() {
        var frames = 0
        for (land in lands) for (age in listOf(Age.OGENJ, Age.VAS)) for ((vw, vh) in listOf(1080 to 2400, 2560 to 1600, 2400 to 1080)) {
            val s = grown(teo.copy(land = land), age)
            val fit = SceneFit.town(vw, vh)
            val kept = VillageRenderer(); val whole = VillageRenderer()
            val a = PixelCanvas(fit.width, fit.height); val b = PixelCanvas(fit.width, fit.height)
            for ((i, step) in steps(s, vw, vh).withIndex()) {
                VillageRenderer.groundKept = true
                kept.render(a, step.state, step.frame)
                VillageRenderer.groundKept = false
                whole.render(b, step.state, step.frame)
                assertArrayEquals("${land.kind}:${land.seed} $age ${fit.width}x${fit.height} step $i (${step.frame.lens.k}, ${step.frame.hour} h)", b.pixels, a.pixels)
                frames++
            }
        }
        println("$frames frames the same")
    }

    @Test fun `the compact strip keeps its ground too`() {
        for (land in lands.take(3)) {
            val s = grown(teo.copy(land = land), Age.VAS)
            val kept = VillageRenderer(); val whole = VillageRenderer()
            val a = PixelCanvas(360, 174); val b = PixelCanvas(360, 174)
            for (i in 0 until 6) {
                val f = Frame(time = 1.0 + i * 0.37, hour = 12f + i, month = 8, compact = true, sea = true, calm = true)
                VillageRenderer.groundKept = true
                kept.render(a, s, f)
                VillageRenderer.groundKept = false
                whole.render(b, s, f)
                assertArrayEquals("${land.kind} compact frame $i", b.pixels, a.pixels)
            }
        }
    }

    /**
     * Teo's coast on his tablet (2560 × 1600 px: a 320 × 200 canvas) and on a phone held upright, at every detail: the kept
     * ground's frame costs well under the whole one's, the classic land's for reference.
     */
    @Test fun `the kept ground costs less than the ground drawn whole`() {
        for ((vw, vh) in listOf(2560 to 1600, 1080 to 2400)) {
            val fit = SceneFit.town(vw, vh)
            val rig = CameraRig.town(fit, vw, vh)
            val comp = VillageLayout.composition(fit.width, fit.height, false)
            for ((name, s) in listOf("coast" to teo, "coast-vas" to grown(teo, Age.VAS), "classic-vas" to grown(teo.copy(land = Land.CLASSIC), Age.VAS))) for (k in 1..3) {
                val t = Terrain.of(s)
                val at = if (t.sea != null) comp.fireX + t.shoreX(0f) + 20f else comp.fireX.toFloat()
                val lens = Lens.of(k, rig.lookAt(at, comp.fireY - 20f, (rig.minScale * k).toFloat()), vw, vh, fit.width, fit.height, fit.width, fit.height)
                val c = PixelCanvas(fit.width, fit.height)
                fun median(keep: Boolean): Double {
                    VillageRenderer.groundKept = keep
                    val r = VillageRenderer()
                    val frame = Frame(hour = 22f, month = 9, lens = lens, sea = true)
                    repeat(15) { r.render(c, s, frame.copy(time = it / 12.0)) }
                    // each frame in this thread's CPU time (FrameBudget)
                    val ms = DoubleArray(31) { i -> FrameBudget.cpuMs { r.render(c, s, frame.copy(time = 2 + i / 12.0)) } }
                    return ms.sorted()[ms.size / 2]
                }
                val whole = median(false); val kept = median(true)
                println("%s %dx%d k=%d: %.2f ms a frame with the ground whole, %.2f kept".format(name, fit.width, fit.height, k, whole, kept))
                assertTrue("$name ${fit.width}x${fit.height} k=$k: kept $kept ms, whole $whole ms", kept < whole * 0.85 || kept < FrameBudget.scaled(2.0))
            }
        }
    }
}
