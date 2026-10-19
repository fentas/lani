package si.lanisce.lani.game.render

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Landmark
import si.lanisce.lani.game.Projects
import kotlin.math.max
import kotlin.math.min

/**
 * Where the road crosses the stream (GAME.md, "The crossing"; Jan, 2026-10-19: one bridge, the sturdy flat one, and the
 * village builds it). From Zaselek on the road goes on west over the stream ([Terrain.bridge]): until the village's
 * bridge (the project "most", [BRIDGE]) has its deck it fords the stream there on stepping stones; the bridge is built at
 * the crossing step by step (the stream measured, stones gathered, the pillars, the logs, the deck, the railings), and
 * from the step that lays its deck ([DECKED]) the road goes over it. There is no other bridge on the map. Pure.
 */
internal object Crossing {
    /** The project, and its landmark, that builds the bridge. */
    const val BRIDGE = "most"

    /** The bridge's steps as drawn (its project's stages spread over them), and the one that lays its deck. */
    const val STEPS = 6
    const val DECKED = 5

    /** How far the bridge reaches past the road's half width either side (cells): its railings stand just off the road. */
    const val SIDE = 0.1f

    /** The step drawn for stage [stage] of [stages] (0: not begun). */
    fun step(stage: Int, stages: Int): Int =
        if (stage <= 0) 0 else ((stage * STEPS.toFloat() / stages.coerceAtLeast(1)) + 0.999f).toInt().coerceIn(1, STEPS)

    /** The step the bridge [bridge] (its landmark, or null: not begun) is drawn at. */
    fun step(bridge: Landmark?): Int = if (bridge == null) 0 else step(bridge.stage, bridge.stages)

    /** Whether the road fords the stream on stepping stones: it goes west ([west]) and the bridge has no deck yet. */
    fun stones(west: Boolean, bridge: Landmark?): Boolean = west && step(bridge) < DECKED

    /** [stones] for [state] as it stands (the road goes west from Zaselek). */
    fun stones(state: GameState): Boolean = stones(state.age >= Age.ZASELEK, bridgeOf(state))

    /** Whether [state]'s road goes over the bridge: it goes west and the bridge has its deck. */
    fun bridged(state: GameState): Boolean = state.age >= Age.ZASELEK && step(bridgeOf(state)) >= DECKED

    /** The bridge's landmark in [state], or null before its first step (or in a culture without the project). */
    fun bridgeOf(state: GameState): Landmark? = Projects.landmarks(state).firstOrNull { it.id == BRIDGE }

    /** Where the stream runs aslant under the bridge, its ends stand at least this far (cells) past the water along each side. */
    const val BANK = 0.3f

    /**
     * The bridge's footprint on [land] for a road [roadHalf] cells either side of its middle: from bank to bank along the
     * road ([Terrain.bridgeHalf] either side of the stream's middle; where the stream runs aslant under it, longer, so its
     * ends stand [BANK] past the water along both its sides too) and [SIDE] wider than the road on each side: x0, y0, w, d
     * (world cells).
     */
    fun box(land: Terrain, roadHalf: Float): FloatArray {
        val half = roadHalf + SIDE
        val y0 = land.bridge[1] - half; val y1 = land.bridge[1] + half
        var x0 = land.bridge[0] - land.bridgeHalf; var x1 = land.bridge[0] + land.bridgeHalf
        var y = y0
        while (true) {
            val s = land.streamX(y)
            x0 = min(x0, s - land.streamHalf - BANK); x1 = max(x1, s + land.streamHalf + BANK)
            if (y >= y1) break
            y = min(y + 0.1f, y1)
        }
        return floatArrayOf(x0, y0, x1 - x0, y1 - y0)
    }
}
