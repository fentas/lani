package si.lanisce.lani.game.scene

import si.lanisce.lani.game.render.Noise
import kotlin.math.floor

/**
 * When the lightning flashes, on the picture's clock (its seconds: [SceneFrame.time], the village's frame time): one flash
 * in each [period], up to [late] seconds into it. A scene's sky with `lightning` (SCENES.md, "Sky cues") flashes by
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
         * A scene's: a flash every 7.3 s, up to 2.5 s late (so every 4.8 to 9.8 s), drawn as a flicker of 0.08 s and an
         * echo at 0.15 to 0.22 s (`Weather.flash`).
         */
        val SCENE = Lightning(7.3, 2.5, 81)

        /** The village's storm event: a flash every 5.3 s, on the dot. */
        val STORM = Lightning(5.3, 0.0, 81)

        /** A dialog's lightning kept over the village for the day ([DaySky]): a flash every 8.1 s, on the dot. */
        val KEPT = Lightning(8.1, 0.0, 81)

        /** The village's lightning: the storm event's, else the kept sky's. */
        fun village(storm: Boolean): Lightning = if (storm) STORM else KEPT

        /** How much of its period a village flash shows (0.3 s in a storm): the bolt and the whitened sky, fading. */
        const val VILLAGE_SHOWS = 0.06f
    }
}
