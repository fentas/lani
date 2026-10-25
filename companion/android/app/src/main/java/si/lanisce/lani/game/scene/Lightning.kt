package si.lanisce.lani.game.scene

import si.lanisce.lani.game.render.Noise
import kotlin.math.floor

/**
 * When the lightning flashes, on the picture's clock (its seconds: [SceneFrame.time], the village's frame time): one flash
 * in each [period], up to [late] seconds into it, each one soft pulse ([glow]). A scene's sky with `lightning` (SCENES.md, "Sky cues") flashes by
 * [SCENE]; the village by [village] (the storm event, or a dialog's sky kept for the day). Pure: the painters draw each flash
 * from here (game/render/scene/Weather.kt, game/render/VillageRenderer.kt), and the thunder after it is timed from here
 * (game/ambient/Thunder.kt), so the flash and its sound agree.
 */
class Lightning private constructor(val period: Double, val late: Double, private val salt: Int) {
    init {
        require(period > 0.0 && late in 0.0..period * 0.5) { "a flash in each period, inside it" }
    }

    /** When flash [n] begins: the one of the [n]-th period, a new bolt each time. */
    fun at(n: Int): Double = n * period + late * Noise.rnd(n, salt)

    /** The period [t] falls in: whose flash the picture draws at [t] (its bolt). */
    fun index(t: Double): Int = floor(t / period).toInt()

    /** Seconds since the flash of [t]'s period began; below 0 it is still to come. */
    fun since(t: Double): Double = t - at(index(t))

    /** The flashes that begin in [from, to) (seconds of the picture's clock), in order; none if [to] isn't after [from]. */
    fun between(from: Double, to: Double): List<Int> {
        if (!(to > from)) return emptyList()
        val out = ArrayList<Int>(2)
        for (n in index(from)..index(to)) {
            val a = at(n)
            if (a >= from && a < to) out.add(n)
        }
        return out
    }

    companion object {
        /**
         * A scene's: a flash every 9 s, up to 4 s late (so every 5 to 13 s), drawn as one soft flash ([glow]): no flicker,
         * no echo (`Weather.flash`).
         */
        val SCENE = Lightning(9.0, 4.0, 81)

        /** The village's storm event: a flash every 10 s, up to 5 s late (every 5 to 15 s), now and then, never in a rhythm. */
        val STORM = Lightning(10.0, 5.0, 81)

        /** A dialog's lightning kept over the village for the day ([DaySky]): a flash every 13 s, up to 5 s late. */
        val KEPT = Lightning(13.0, 5.0, 81)

        /** The village's lightning: the storm event's, else the kept sky's. */
        fun village(storm: Boolean): Lightning = if (storm) STORM else KEPT

        /** A flash brightens over this many seconds … */
        const val RISE_S = 0.06

        /** … and fades over this many after (an ease out): one soft flash, never a flicker. */
        const val FADE_S = 0.5

        /** How long a flash shows, all told (s). */
        const val SHOWS_S = RISE_S + FADE_S

        /** How long the bolt itself shows at the flash's start (s). */
        const val BOLT_S = 0.18

        /**
         * How bright a flash is [since] seconds after it began, 0..1: a quick soft rise to 1 over [RISE_S], then an ease out
         * over [FADE_S]; 0 before and after. The village and the scenes draw their flash with it, so every flash is one soft
         * pulse well under the three a second that would count as flashing (WCAG 2.3.1), and none comes within 5 s of the next.
         */
        fun glow(since: Double): Float = when {
            since < 0.0 || since >= SHOWS_S -> 0f
            since < RISE_S -> (since / RISE_S).toFloat().let { it * it * (3f - 2f * it) }
            else -> (1.0 - (since - RISE_S) / FADE_S).toFloat().let { it * it }
        }
    }
}
