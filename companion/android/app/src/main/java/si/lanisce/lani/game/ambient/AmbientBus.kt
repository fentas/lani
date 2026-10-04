package si.lanisce.lani.game.ambient

import kotlin.math.min

/**
 * How loud the background sounds are as a whole, and how fast that changes (the rules the player follows, pure): the
 * learner's volume, lower still while a voice speaks (they duck). Every change is a fade, nothing sudden; the one
 * exception is the microphone and the app leaving the screen ([silenced]): then they stop at once and fade in again after.
 */
object AmbientBus {
    /**
     * The volume setting's default: low, well under the voices. Its square ([master]) is 0.14 (−17 dB), 5 dB under the
     * quarter it was at first. A learner who has moved the slider keeps their own (the phone keeps only a level set).
     */
    const val DEFAULT_VOLUME = 0.375f

    /** Under a voice: this share of the level (about −10 dB). */
    const val DUCK = 0.3f

    /** A loop fades in or out over this many seconds when the place, the hour or the weather changes; the whole too. */
    const val FADE_S = 1.5f

    /** Down under a voice this fast … */
    const val DUCK_S = 0.3f

    /** … and back up after it. */
    const val UNDUCK_S = 1.2f

    /** In again after the microphone, the background or silence. */
    const val RETURN_S = 2f

    /**
     * The level of the whole for the volume setting [volume] (0..1): its square, so the slider's middle is a quarter
     * (−12 dB) and its low end fine-grained.
     */
    fun master(volume: Float): Float = volume.coerceIn(0f, 1f).let { it * it }

    /** Where the whole is heading: nothing when switched off; under a voice, ducked. */
    fun target(on: Boolean, volume: Float, talking: Boolean): Float = when {
        !on -> 0f
        talking -> master(volume) * DUCK
        else -> master(volume)
    }

    /** They stop at once, not with a fade: the microphone records (they'd spoil speech recognition), or the app is away. */
    fun silenced(micOpen: Boolean, away: Boolean): Boolean = micOpen || away

    /** How many seconds a fade of the whole takes from [from] to [to] ([Fader]): down quickly under a voice, back up slowly. */
    fun seconds(from: Float, to: Float, talking: Boolean): Float = when {
        to < from -> if (talking) DUCK_S else FADE_S
        from <= 0f -> RETURN_S
        else -> UNDUCK_S
    }
}

/**
 * A level that fades to each new target from wherever it is, over the seconds [step] is given when the target changes,
 * whatever the distance: slow at both ends (a smoothstep), so nothing starts or stops with a jolt. A new target midway
 * starts a new fade from where the level is.
 */
class Fader(value: Float = 0f) {
    var value = value
        private set
    private var from = value
    private var to = value
    /** How far the fade has come, 0..1, and how long it takes. */
    private var t = 1f
    private var span = 0f

    /** The level after [dt] seconds more on its way to [target]; a new target fades over [seconds]. */
    fun step(target: Float, dt: Float, seconds: Float): Float {
        if (target != to) {
            from = value
            to = target
            t = 0f
            span = seconds
        }
        t = if (span <= 0f) 1f else min(1f, t + dt / span)
        value = if (t >= 1f) to else from + (to - from) * t * t * (3f - 2f * t)
        return value
    }

    /** At [v] at once, resting there. */
    fun set(v: Float) {
        value = v
        from = v
        to = v
        t = 1f
    }
}
