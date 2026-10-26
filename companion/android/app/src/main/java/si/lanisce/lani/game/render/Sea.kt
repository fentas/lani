package si.lanisce.lani.game.render

import kotlin.math.floor

/**
 * How a coast's sea looks, near and far, as one water (see [Terrain.sea]; GAME.md, "The land"): the land's sea from the
 * beach up to the horizon's row (the renderer's ground), and beyond it the far sea (the [Backdrop]) up to where it meets
 * the sky. Both take the same colours by how far off they are, the same glints, and the same light: toward the horizon
 * the water takes on more of the sky's colour (the haze of the distance), in dithered steps like the sky's bands.
 *
 * Until 2026-10-26 the two were drawn apart: the far sea was a band 6 nominal px above the horizon, laid out to stand
 * behind the far treeline, with its own colours and its own drifting glints. Where a coast's sea comes up to the horizon
 * the treeline is left out, so between the band and the land's sea a strip of the hills behind showed, dark at night, and
 * the band read as a separate sea moving behind the village (Teo's coast, Friuli, land seed 551323397).
 */
internal object Sea {
    /** How much of the sky's colour the sea takes at the horizon's row. */
    const val HAZE = 0.3f

    /** How much at the far line where it meets the sky. */
    const val HAZE_FAR = 0.44f

    /** Over how many nominal rows below the horizon the haze fades out: the near sea has the water's own colours. */
    const val HAZE_ROWS = 28f

    /** How far above the horizon the far sea reaches on the left of a coast (nominal rows): its line against the sky. */
    const val FAR_ROWS = 10.5f

    /** The haze's steps (dithered between, see [hazeAt]). */
    const val STEP = 0.04f

    /** The far sea's glints, the near sea's twinkle: a short dash of light, a new one now and then (see [glint]). */
    const val GLINT = 0.08f

    /**
     * How far right of where the land's sea and its beach end at the horizon the far treeline rises to its height (nominal
     * px): the far trees end low at the beach, the far sea down to the horizon behind them (see [Backdrop.treeline]).
     */
    const val TREELINE_TAPER = 8f

    /**
     * The far sea's line where it meets the sky under [env]: pale by day, as much as the culture's band along the horizon
     * has ([Backdrop]), fainter by night, when there is little light for it to catch.
     */
    fun line(env: Env): Int = Col.mix(env.skyHorizon, Col.hex(0xFFFFFF), 0.35f * (1f - 0.6f * env.dark))

    /**
     * The haze at [d] nominal rows below the horizon (negative: above it, on the far sea), at canvas pixel ([x], [y]): in
     * steps of [STEP], dithered between them, so a slow gradient over many rows reads as the sky's bands do.
     */
    fun hazeAt(d: Float, x: Int, y: Int): Float {
        val h = if (d >= 0f) HAZE * (1f - d / HAZE_ROWS) else HAZE + (HAZE_FAR - HAZE) * (-d / FAR_ROWS).coerceAtMost(1f)
        if (h <= 0f) return 0f
        return (floor(h / STEP + Dither.at(x, y) - 0.5f) * STEP).coerceAtLeast(0f)
    }

    /**
     * Whether the sea glints at nominal ([bx], [by]) at the water's tick [t] (three a second): short dashes that come and
     * go, the same on the near sea and the far one (the stream, the pond and a lake twinkle alike, a little more often).
     */
    fun glint(bx: Int, by: Int, t: Int, chance: Float = GLINT): Boolean = Noise.rnd(bx / 2, by, t + bx / 7) < chance
}
