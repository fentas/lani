package si.lanisce.lani.game.ambient

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin

class AmbientMixerTest {
    private val n = Layer.entries.size
    private fun targets(vararg levels: Pair<Layer, Float>) = FloatArray(n).also { t -> levels.forEach { (l, v) -> t[l.ordinal] = v } }

    /** Mixes blocks for [seconds]; the last block. */
    private fun AmbientMixer.run(seconds: Float, t: FloatArray, bus: Float, busSeconds: Float = AmbientBus.FADE_S, size: Int = 480): ShortArray {
        val out = ShortArray(size)
        repeat((seconds * rate / size).roundToInt()) { mix(out, t, bus, busSeconds) }
        return out
    }

    @Test fun `the bus is quiet by default, ducks under a voice and is off when switched off`() {
        val quiet = AmbientBus.target(on = true, volume = AmbientBus.DEFAULT_VOLUME, talking = false)
        assertEquals(0.140625f, quiet, 1e-6f)
        // about 5 dB under the quarter (−12 dB) it was at first, and the slider's top still the whole
        assertEquals(-5f, (20 * log10(quiet / 0.25f)), 0.1f)
        assertEquals(1f, AmbientBus.master(1f), 0f)
        assertEquals(quiet * AmbientBus.DUCK, AmbientBus.target(true, AmbientBus.DEFAULT_VOLUME, talking = true), 1e-6f)
        assertEquals(0f, AmbientBus.target(on = false, volume = 1f, talking = false), 0f)
        assertEquals(1f, AmbientBus.master(3f), 0f)
        assertEquals(0f, AmbientBus.master(-1f), 0f)
        assertTrue(AmbientBus.silenced(micOpen = true, away = false))
        assertTrue(AmbientBus.silenced(micOpen = false, away = true))
        assertFalse(AmbientBus.silenced(micOpen = false, away = false))
    }

    @Test fun `down under a voice quickly, back slowly, in after silence slower still`() {
        assertEquals(AmbientBus.DUCK_S, AmbientBus.seconds(0.25f, 0.075f, talking = true), 0f)
        assertEquals(AmbientBus.UNDUCK_S, AmbientBus.seconds(0.075f, 0.25f, talking = false), 0f)
        assertEquals(AmbientBus.RETURN_S, AmbientBus.seconds(0f, 0.25f, talking = false), 0f)
        assertEquals(AmbientBus.FADE_S, AmbientBus.seconds(0.25f, 0f, talking = false), 0f)
        assertTrue(AmbientBus.FADE_S in 1f..2f && AmbientBus.RETURN_S in 1f..2f)
    }

    @Test fun `a fade takes its seconds whatever the distance, slow at both ends`() {
        for (to in listOf(1f, 0.6f, 0.1f)) {
            val f = Fader()
            var steps = 0
            while (f.value != to) { f.step(to, 0.01f, 1.5f); steps++ }
            assertEquals("to $to", 150f, steps.toFloat(), 1f)
        }
        val out = Fader(0.8f)
        var steps = 0
        while (out.value > 0f) { out.step(0f, 0.01f, 1.5f); steps++ }
        assertEquals("out to nothing", 150f, steps.toFloat(), 1f)
        // slow at the start: a tenth of the time, far less than a tenth of the way
        val slow = Fader()
        repeat(15) { slow.step(1f, 0.01f, 1.5f) }
        assertTrue(slow.value < 0.05f)
        // a new target midway fades on from where the level is, over the new fade's seconds
        val turn = Fader()
        repeat(75) { turn.step(1f, 0.01f, 1.5f) }
        val mid = turn.value
        assertEquals(0.5f, mid, 0.01f)
        turn.step(0f, 0.01f, 0.3f)
        assertTrue(turn.value <= mid && turn.value > mid - 0.02f)
        repeat(30) { turn.step(0f, 0.01f, 5f) } // the seconds count when the fade begins
        assertEquals(0f, turn.value, 0f)
        assertEquals(0.7f, Fader().step(0.7f, 0.01f, 0f), 0f)
        val rest = Fader(0.3f)
        assertEquals(0.3f, rest.step(0.3f, 1f, 1f), 0f)
    }

    @Test fun `nothing sounds without a loop, a level or the bus`() {
        val m = AmbientMixer()
        val rain = targets(Layer.RAIN to 1f)
        assertTrue("not loaded", m.silent(rain, 0.25f))
        m.load(Layer.RAIN, ShortArray(100) { 1000 })
        assertFalse(m.silent(rain, 0.25f))
        assertTrue("no level", m.silent(targets(), 0.25f))
        assertTrue("the bus at nothing and staying there", m.silent(rain, 0f))
        m.load(Layer.BIRDS, ShortArray(0))
        assertFalse("an empty loop isn't a loop", m.loaded(Layer.BIRDS))
    }

    @Test fun `a loop fades in over the fade and then plays at its level, its trim and the bus`() {
        val m = AmbientMixer()
        m.load(Layer.RAIN, ShortArray(1000) { 10_000 })
        val t = targets(Layer.RAIN to 0.5f)
        val first = m.run(0.02f, t, 1f)
        assertTrue("it starts from nothing", abs(first[0].toInt()) < 100)
        val later = m.run(AmbientBus.FADE_S + 0.5f, t, 1f)
        assertEquals(0.5f, m.gain(Layer.RAIN), 1e-4f)
        assertEquals(1f, m.bus, 1e-4f)
        assertEquals(10_000 * 0.5f * Layer.RAIN.trim, later.last().toFloat(), 2f)
        // and out again
        m.run(AmbientBus.FADE_S + 0.1f, targets(), 1f)
        assertEquals(0f, m.gain(Layer.RAIN), 0f)
        assertTrue(m.silent(targets(), 1f))
    }

    @Test fun `a loop runs on round its end without a seam`() {
        val m = AmbientMixer()
        val pcm = ShortArray(700) { (it * 10).toShort() }
        m.load(Layer.WIND, pcm, start = 650)
        val t = targets(Layer.WIND to 1f)
        m.run(AmbientBus.FADE_S + 0.2f, t, 1f, busSeconds = 0.01f) // faded in: every sample as it is, times the trim
        val out = ShortArray(900)
        m.mix(out, t, 1f, 0.01f)
        // it goes on from where it was, round the end and on again: each sample the next of the loop
        val at = pcm.indexOfFirst { abs(it * Layer.WIND.trim - out[0]) <= 1f }
        for (i in out.indices) assertEquals("sample $i", pcm[(at + i) % pcm.size] * Layer.WIND.trim, out[i].toFloat(), 1f)
    }

    @Test fun `stopped at once, the whole comes back with a fade`() {
        val m = AmbientMixer()
        m.load(Layer.SEA, ShortArray(1000) { 8_000 })
        val t = targets(Layer.SEA to 1f)
        m.run(3f, t, 0.25f)
        assertEquals(0.25f, m.bus, 1e-4f)
        m.stop()
        assertEquals(0f, m.bus, 0f)
        val next = ShortArray(480)
        m.mix(next, t, 0.25f, AmbientBus.RETURN_S)
        assertTrue("it fades in", abs(next.last().toInt()) < 8_000 * 0.25f * Layer.SEA.trim * 0.1f)
    }

    @Test fun `loud loops together are held within the samples' range`() {
        val m = AmbientMixer()
        for (l in Layer.entries) m.load(l, ShortArray(500) { 30_000 })
        val all = FloatArray(n) { 1f }
        val out = m.run(3f, all, 1f)
        assertTrue(out.all { it == 32_767.toShort() })
    }

    @Test fun `a short sound plays once from its delay on, at its gain and the whole's, then it's done`() {
        val m = AmbientMixer()
        val none = targets()
        m.run(AmbientBus.FADE_S + 0.1f, none, 0.5f) // the whole at 0.5
        val shot = ShortArray(300) { 10_000 }
        m.play(shot, gain = 0.8f, delay = 100)
        assertEquals(1, m.playing)
        assertFalse("a sound playing isn't silence", m.silent(none, 0.5f))
        val out = ShortArray(480)
        m.mix(out, none, 0.5f, AmbientBus.FADE_S)
        for (i in 0 until 100) assertEquals("before it, sample $i", 0, out[i].toInt())
        // its last sample ends it: 299 of them are heard
        for (i in 100 until 399) assertEquals("sample $i", 10_000 * 0.8f * 0.5f, out[i].toFloat(), 1f)
        for (i in 399 until 480) assertEquals("after it, sample $i", 0, out[i].toInt())
        assertEquals(0, m.playing)
        assertTrue(m.silent(none, 0.5f))
    }

    @Test fun `a short sound waits across blocks, runs on over them, and twice as fast is over in half the time`() {
        val m = AmbientMixer()
        val none = targets()
        m.run(AmbientBus.FADE_S + 0.1f, none, 1f)
        val ramp = ShortArray(1001) { (it * 10).toShort() }
        m.play(ramp, gain = 1f, speed = 2f, delay = 700)
        val a = ShortArray(480)
        m.mix(a, none, 1f, 0.01f)
        assertTrue(a.all { it.toInt() == 0 })
        val b = ShortArray(480)
        m.mix(b, none, 1f, 0.01f)
        // 700 samples in: the second block's 220th; every other sample of it, interpolated where it falls between
        for (i in 220 until 480) assertEquals("sample $i", ((i - 220) * 2 * 10).toFloat(), b[i].toFloat(), 1f)
        val c = ShortArray(480)
        m.mix(c, none, 1f, 0.01f)
        for (i in 0 until 240) assertEquals("sample $i", ((i + 260) * 2 * 10).toFloat(), c[i].toFloat(), 1f)
        assertTrue(c.drop(240).all { it.toInt() == 0 })
        assertEquals(0, m.playing)
        // a bit slower: between the samples
        m.play(ShortArray(100) { (it * 100).toShort() }, gain = 1f, speed = 0.5f)
        val d = ShortArray(10)
        m.mix(d, none, 1f, 0.01f)
        assertEquals(listOf(0, 50, 100, 150, 200), d.take(5).map { it.toInt() })
    }

    @Test fun `short sounds join the loops under the whole, stopping or hushing drops them, and too many are left out`() {
        val m = AmbientMixer()
        m.load(Layer.RAIN, ShortArray(1000) { 1_000 })
        val rain = targets(Layer.RAIN to 1f)
        m.run(AmbientBus.FADE_S + 0.1f, rain, 1f, busSeconds = 0.01f)
        m.play(ShortArray(2000) { 2_000 }, gain = 0.5f)
        val out = ShortArray(480)
        m.mix(out, rain, 1f, 0.01f)
        assertEquals(1_000 * Layer.RAIN.trim + 1_000, out[10].toFloat(), 1f)
        m.hush()
        assertEquals(0, m.playing)
        m.mix(out, rain, 1f, 0.01f)
        assertEquals(1_000 * Layer.RAIN.trim, out[10].toFloat(), 1f)
        repeat(AmbientMixer.MAX_VOICES + 5) { m.play(ShortArray(24_000) { 10 }, 1f) }
        assertEquals(AmbientMixer.MAX_VOICES, m.playing)
        m.stop()
        assertEquals(0, m.playing)
        // nothing to play: nothing kept
        m.play(ShortArray(1), 1f)
        m.play(ShortArray(100), 0f)
        assertEquals(0, m.playing)
    }

    @Test fun `one period is cut from the middle of a padded file, as it was made`() {
        val period = 24_000
        val wave = { i: Int -> (8_000 * sin(2 * PI * 3 * i / period)).roundToInt().toShort() }
        // the file: the loop's last quarter second, the loop, its first quarter second
        val file = ShortArray(period + 2 * LoopCut.PAD) { wave(it - LoopCut.PAD) }
        val loop = LoopCut.cut(file, period)
        assertArrayEquals(ShortArray(period) { wave(it) }, loop)
    }

    @Test fun `a file the decoder shifted still loops without a jump`() {
        val period = 24_000
        val wave = { i: Int -> (8_000 * sin(2 * PI * 5 * i / period + 0.4)).roundToInt().toShort() }
        // three samples lost at the start, a few more at the end
        val file = ShortArray(period + 2 * LoopCut.PAD - 10) { wave(it + 3 - LoopCut.PAD) }
        val loop = LoopCut.cut(file, period)
        assertEquals(period, loop.size)
        val step = (0 until period - 1).maxOf { abs(loop[it + 1] - loop[it]) }
        assertTrue("the seam is no bigger a step than the wave's own", abs(loop[0] - loop[period - 1]) <= step + 2)
    }

    @Test fun `a file without its pads loops as it is, its start blended in from its end`() {
        val file = ShortArray(4_000) { (it % 1000).toShort() }
        val loop = LoopCut.cut(file, 24_000)
        assertEquals(4_000 - LoopCut.FADE, loop.size)
        // round the seam: the last sample, then the first, close to how the file ran on
        assertTrue(abs(loop[0] - file[file.size - LoopCut.FADE]) < 10)
        assertEquals(0, LoopCut.cut(ShortArray(0), 100).size)
    }
}
