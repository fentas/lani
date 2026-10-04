package si.lanisce.lani.game.ambient

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Mixes the background loops into blocks of 16-bit mono samples (pure: the player, data/AmbientPlayer.kt, writes the
 * blocks to the phone's audio): each loop at its level, fading toward its target ([AmbientBus.FADE_S]), all of them at the
 * level of the whole ([AmbientBus]). A loop not loaded yet waits silently and fades in once it is. Short sounds played once
 * ([play]: the thunder, a bell, the anvil …) join in at their gain, under the level of the whole like the loops, so they
 * duck under a voice with them.
 */
class AmbientMixer(val rate: Int = RATE) {
    private val n = Layer.entries.size
    private val loops = arrayOfNulls<ShortArray>(n)
    private val gains = Array(n) { Fader() }
    private val pos = IntArray(n)
    private var acc = FloatArray(0)
    private val whole = Fader()
    private val voices = ArrayList<Voice>()

    /** A short sound playing once: [pcm] from [pos] on, [speed] samples of it a sample, at [gain], after [wait] samples. */
    private class Voice(val pcm: ShortArray, val gain: Float, val speed: Float, var wait: Int) {
        var pos = 0.0
    }

    /** The level of the whole now. */
    val bus: Float get() = whole.value

    /** How many short sounds are playing or about to. */
    val playing: Int get() = voices.size

    /**
     * Stopped at once (the microphone, the background): the whole fades in from nothing when it plays again, and the short
     * sounds playing are gone.
     */
    fun stop() {
        whole.set(0f)
        voices.clear()
    }

    /** The short sounds playing are gone at once (switched off, nowhere to be heard); the loops go on. */
    fun hush() = voices.clear()

    /**
     * [pcm] played once from [delay] samples into the next block on, at [gain] (0..1, then the level of the whole),
     * [speed] times as fast (and as high: linearly between samples). At most [MAX_VOICES] at a time: more are left out.
     */
    fun play(pcm: ShortArray, gain: Float, speed: Float = 1f, delay: Int = 0) {
        if (pcm.size < 2 || gain <= 0f || voices.size >= MAX_VOICES) return
        voices.add(Voice(pcm, gain.coerceIn(0f, 1f), speed.coerceIn(0.25f, 4f), max(0, delay)))
    }

    /** [layer]'s loop, one period of it ([LoopCut]); it starts at [start] (a sample of it), so the loops don't all begin together. */
    fun load(layer: Layer, pcm: ShortArray, start: Int = 0) {
        if (pcm.isEmpty()) return
        loops[layer.ordinal] = pcm
        pos[layer.ordinal] = start.mod(pcm.size)
    }

    fun loaded(layer: Layer): Boolean = loops[layer.ordinal] != null

    /** [layer]'s level now (before its trim and the whole's). */
    fun gain(layer: Layer): Float = gains[layer.ordinal].value

    /**
     * Nothing sounds, nor is about to: the whole is at nothing and staying there, or no short sound plays and no loop is at
     * a level or heading for one.
     */
    fun silent(targets: FloatArray, busTarget: Float): Boolean =
        (bus <= 0f && busTarget <= 0f) ||
            (voices.isEmpty() && (0 until n).all { (gains[it].value <= 0f && targets[it] <= 0f) || loops[it] == null })

    /**
     * The next [out].size samples: each loaded loop fading toward its level in [targets] (by [Layer] ordinal, 0..1) over
     * [AmbientBus.FADE_S], the whole toward [busTarget] over [busSeconds] ([Fader]). The levels move smoothly within the
     * block, no steps.
     */
    fun mix(out: ShortArray, targets: FloatArray, busTarget: Float, busSeconds: Float) {
        val size = out.size
        if (acc.size != size) acc = FloatArray(size)
        val a = acc
        a.fill(0f)
        val dt = size.toFloat() / rate
        for (l in 0 until n) {
            val pcm = loops[l] ?: continue
            val g0 = gains[l].value
            val g1 = gains[l].step(targets[l].coerceIn(0f, 1f), dt, AmbientBus.FADE_S)
            if (g0 <= 0f && g1 <= 0f) continue
            val trim = Layer.entries[l].trim
            val step = (g1 - g0) / size
            var g = g0
            var p = pos[l]
            val len = pcm.size
            for (i in 0 until size) {
                a[i] += pcm[p] * g * trim
                g += step
                if (++p == len) p = 0
            }
            pos[l] = p
        }
        if (voices.isNotEmpty()) voices(a)
        val b0 = whole.value
        val b1 = whole.step(busTarget.coerceIn(0f, 1f), dt, busSeconds)
        val bStep = (b1 - b0) / size
        var b = b0
        for (i in 0 until size) {
            out[i] = (a[i] * b).roundToInt().coerceIn(-32768, 32767).toShort()
            b += bStep
        }
    }

    /** The short sounds into [a], each from where it is; one that has run out is done. */
    private fun voices(a: FloatArray) {
        val size = a.size
        val it = voices.iterator()
        while (it.hasNext()) {
            val v = it.next()
            if (v.wait >= size) {
                v.wait -= size
                continue
            }
            var i = v.wait
            v.wait = 0
            val pcm = v.pcm
            val last = pcm.size - 1
            var p = v.pos
            while (i < size) {
                val j = p.toInt()
                if (j >= last) break
                val f = (p - j).toFloat()
                a[i] += (pcm[j] + (pcm[j + 1] - pcm[j]) * f) * v.gain
                p += v.speed
                i++
            }
            v.pos = p
            if (p.toInt() >= last) it.remove()
        }
    }

    companion object {
        /** The loops' sample rate, and the player's. */
        const val RATE = 24_000

        /** Short sounds at a time, at most: a storm's thunder overlapping and a bell's peal. */
        const val MAX_VOICES = 16
    }
}

/**
 * One period out of a loop's decoded file (see [Layer]): the file holds [PAD] samples of the loop's end before its first
 * sample and as many of its start after the last, so a lossy codec's edges never fall on the loop point. The period is
 * cut from the middle, and its last [FADE] samples blend into the ones just before its first sample: the seam is
 * smooth even when the decoder shifted or trimmed the file by a few samples.
 */
object LoopCut {
    /** The samples of the loop's end before its start, and of its start after its end, in each file. */
    const val PAD = 6_000

    /** The blend at the seam: 20 ms. */
    const val FADE = 480

    /**
     * One [period] of [decoded]. A file too short to have its pads (not one of ours) loops as it is, a [FADE] shorter:
     * its start blends in from its end.
     */
    fun cut(decoded: ShortArray, period: Int): ShortArray {
        if (decoded.isEmpty() || period <= 0) return ShortArray(0)
        if (decoded.size >= period + PAD) {
            val off = minOf(PAD, decoded.size - period)
            val out = decoded.copyOfRange(off, off + period)
            // the tail blends into the samples just before the first: for a true loop they are the same, so nothing changes
            val f = minOf(FADE, period / 4)
            for (i in 0 until f) out[period - f + i] = blend(out[period - f + i], decoded[off - f + i], (i + 1f) / (f + 1f))
            return out
        }
        val f = minOf(FADE, decoded.size / 4)
        val out = decoded.copyOf(decoded.size - f)
        for (i in 0 until f) out[i] = blend(decoded[decoded.size - f + i], decoded[i], (i + 1f) / (f + 1f))
        return out
    }

    /** [a] blended toward [b] by [w] (0: all [a]). */
    private fun blend(a: Short, b: Short, w: Float): Short = (a * (1f - w) + b * w).roundToInt().coerceIn(-32768, 32767).toShort()
}
