package si.lanisce.lani.game.render

import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Landscape
import java.lang.management.ManagementFactory

/**
 * What Home's header village makes for the collector (docs/plans/05-built/changes-areas.md): at rest it renders 12
 * pictures a second on a thread of its own, and Home allocated about 3 MB a second for it. A picture of a village of some
 * weeks (huts, a house, a field, the palisade, people) stays under [PICTURE_BYTES] on its render thread (before: 167 KB on
 * the QA learner's village, the footpaths' grid made anew a picture, 64 KB, the festivals' dates, a date parsed from
 * nothing, its trace filled in), and what the main thread makes to ask for a picture (the frame) under [ASK_BYTES].
 */
class HeaderVillageAllocTest {
    private val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private fun allocated() = mx.getThreadAllocatedBytes(Thread.currentThread().id)

    private val village: GameState = Founding("primorska", Landscape.VALLEY, 42L).village(1_000L).let { s ->
        val types = listOf(BuildingType.HUT, BuildingType.FIELD, BuildingType.HOUSE, BuildingType.WELL, BuildingType.HUT, BuildingType.KOZOLEC, BuildingType.BEEHIVE, BuildingType.SMITHY, BuildingType.MARKET)
        s.copy(
            age = Age.VAS, villagers = 6, morale = 70, fire = 70,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i) } + Building("ring", BuildingType.PALISADE, si.lanisce.lani.game.NO_PLOT),
        )
    }

    private fun frame(i: Int, hour: Float) = Frame(time = 1.0 + i / 12.0, hour = hour, month = 10, compact = true, wall = 1_793_000_000_000L + i * 83L)

    @Test fun `a picture of Home's header village makes little for the collector, by day and at night`() {
        val fit = SceneFit.of(1080, 420, true)
        for (hour in listOf(14f, 22.5f)) {
            val canvas = PixelCanvas(fit.width, fit.height)
            val r = VillageRenderer()
            for (i in 0 until 40) r.render(canvas, village, frame(i, hour))
            val a = allocated()
            val n = 120
            for (i in 0 until n) r.render(canvas, village, frame(40 + i, hour))
            val per = (allocated() - a) / n
            println("header village ${fit.width}x${fit.height} at $hour h: $per bytes a picture")
            assertTrue("$per bytes a picture at $hour h (under $PICTURE_BYTES)", per < PICTURE_BYTES)
        }
    }

    @Test fun `asking for a picture makes little on the main thread`() {
        // the night sky's catalog read and the minute's sky placed once (SkyNow.at keeps the minute's)
        si.lanisce.lani.game.sky.SkyCards.now()
        val a = allocated()
        val n = 240
        repeat(n) {
            val now = java.time.LocalDateTime.now()
            Frame(time = it / 12.0, hour = now.hour + now.minute / 60f, month = now.monthValue, compact = true, moon = Moon.now(), skyNow = si.lanisce.lani.game.sky.SkyCards.now(), wall = System.currentTimeMillis())
        }
        val per = (allocated() - a) / n
        println("asking: $per bytes a picture")
        assertTrue("$per bytes an ask (under $ASK_BYTES)", per < ASK_BYTES)
    }

    companion object {
        const val PICTURE_BYTES = 130_000L
        const val ASK_BYTES = 4_000L
    }
}
