package si.lanisce.lani.game.scene

import kotlinx.serialization.Serializable
import si.lanisce.lani.game.GameState
import java.time.LocalDate

// The weather a scene dialog brings (companion/SCENES.md, "Sky cues"): a line or a reply sets new targets, the scene
// eases toward them, and the sky a finished dialog reached stays over its scene and the village until midnight.

/**
 * A change of the weather when a line or a reply plays: new targets, 0..1, for the levels it names; the ones it
 * leaves out keep their value.
 */
@Serializable
data class SkyCue(
    val rain: Float? = null,
    val snow: Float? = null,
    val fog: Float? = null,
    val wind: Float? = null,
    /** Overcast: a greyer sky and a darker light. */
    val gloom: Float? = null,
    /** Now and then a flash, far off. */
    val lightning: Boolean? = null,
)

/** The weather over a scene: each level from 0 (none) to 1 (heavy); with [lightning] it flashes now and then. */
@Serializable
data class Sky(
    val rain: Float = 0f,
    val snow: Float = 0f,
    val fog: Float = 0f,
    val wind: Float = 0f,
    val gloom: Float = 0f,
    val lightning: Boolean = false,
) {
    val clear: Boolean get() = rain <= 0f && snow <= 0f && fog <= 0f && wind <= 0f && gloom <= 0f && !lightning

    /** This sky with [cue]'s targets (none: as it is). */
    operator fun plus(cue: SkyCue?): Sky = if (cue == null) this else Sky(
        rain = cue.rain?.level() ?: rain,
        snow = cue.snow?.level() ?: snow,
        fog = cue.fog?.level() ?: fog,
        wind = cue.wind?.level() ?: wind,
        gloom = cue.gloom?.level() ?: gloom,
        lightning = cue.lightning ?: lightning,
    )

    companion object {
        val CLEAR = Sky()

        /** A cue's change eases in over this many seconds: the rain begins slowly. */
        const val RAMP_S = 4.0
    }
}

private fun Float.level() = coerceIn(0f, 1f)

/** Whether a line or a reply of the dialog changes the sky. */
val Dialog.hasSky: Boolean get() = lines.any { l -> l.sky != null || l.answers.any { it.reply?.sky != null } }

/** Whether the sky the dialog ends with stays for the rest of the day: when it has cues, unless [Dialog.skyStays] is false. */
val Dialog.skyKept: Boolean get() = hasSky && skyStays != false

/**
 * The sky over [base] once the dialog has come to line [at] (the lines before it played, the learner's turn at it
 * shown) with the learner's right [picks] (the choice taken at each turn passed, in order): every line's cue in
 * order, and after a turn the cue of the reply to its pick.
 */
fun Dialog.skyAt(at: Int, picks: List<Int>, base: Sky = Sky.CLEAR): Sky {
    var s = base
    played(at, picks) { sky, _, _ -> s += sky }
    return s
}

/**
 * The cues of the dialog up to line [at], in order: each line played (a learner's turn when it comes up), and after
 * each turn passed the reply to its pick in [picks]: its sky, its effects and its stage directions ([actAt]).
 */
internal inline fun Dialog.played(at: Int, picks: List<Int>, each: (SkyCue?, Map<String, Float>, Map<String, ActCue>) -> Unit) {
    var turn = 0
    for ((i, line) in lines.withIndex()) {
        val turnLine = line.choices.isNotEmpty()
        if (i > at || (i == at && !turnLine)) break // a line not said yet
        each(line.sky, line.fx, line.act)
        if (turnLine && i < at) line.choices.getOrNull(picks.getOrElse(turn++) { -1 })?.reply?.let { each(it.sky, it.fx, it.act) }
    }
}

/** One level easing from [from] to [to] over [Sky.RAMP_S] from [start] (seconds of [SkyEase.clock]), slow at both ends. */
data class Fade(val from: Float, val to: Float, val start: Double) {
    fun at(now: Double): Float {
        val u = ((now - start) / Sky.RAMP_S).coerceIn(0.0, 1.0).toFloat()
        return from + (to - from) * u * u * (3f - 2f * u)
    }

    /** On toward [target] from where it is at [now]; the same target keeps the fade going. */
    fun toward(target: Float, now: Double): Fade = if (target == to) this else Fade(at(now), target, now)

    companion object {
        fun still(v: Float) = Fade(v, v, 0.0)
    }
}

/** The sky on screen: each level eases toward its target on its own (a new cue for one leaves the others' fades be); lightning follows at once. */
data class SkyEase(val rain: Fade, val snow: Fade, val fog: Fade, val wind: Fade, val gloom: Fade, val lightning: Boolean) {
    /** Where it is heading. */
    val target: Sky get() = Sky(rain.to, snow.to, fog.to, wind.to, gloom.to, lightning)

    fun toward(sky: Sky, now: Double): SkyEase = SkyEase(
        rain.toward(sky.rain, now), snow.toward(sky.snow, now), fog.toward(sky.fog, now),
        wind.toward(sky.wind, now), gloom.toward(sky.gloom, now), sky.lightning,
    )

    fun at(now: Double): Sky = Sky(rain.at(now), snow.at(now), fog.at(now), wind.at(now), gloom.at(now), lightning)

    companion object {
        /** Resting at [sky]. */
        fun still(sky: Sky) = SkyEase(Fade.still(sky.rain), Fade.still(sky.snow), Fade.still(sky.fog), Fade.still(sky.wind), Fade.still(sky.gloom), sky.lightning)

        val CLEAR = still(Sky.CLEAR)

        /** The fades' clock: seconds, monotonic. */
        fun clock(): Double = System.nanoTime() / 1e9
    }
}

/**
 * The sky a finished dialog left over [scene], kept in [GameState.sky] on the day [on] (ISO date): it shows there and
 * over the village until midnight, then clears. One a day: the latest finished dialog with cues sets it.
 */
@Serializable
data class DaySky(val on: String, val scene: String, val sky: Sky) {
    companion object {
        /** The kept sky of [today] over [scene], or over the village when [scene] is null; otherwise clear. */
        fun of(state: GameState?, today: LocalDate, scene: String? = null): Sky = of(state, today.toString(), scene)

        /** The same for [today] as an ISO date; an empty one (no day known) is clear. */
        fun of(state: GameState?, today: String, scene: String? = null): Sky {
            val d = state?.sky ?: return Sky.CLEAR
            if (today.isEmpty() || d.on != today || (scene != null && scene != d.scene)) return Sky.CLEAR
            return d.sky
        }

        /** [state] once [dialog] of [scene] ended with the sky [end]: kept when the dialog keeps its sky, a clear one dropped. */
        fun keep(state: GameState, scene: String, dialog: Dialog, end: Sky, today: LocalDate): GameState {
            if (!dialog.skyKept) return state
            val next = if (end.clear) null else DaySky(today.toString(), scene, end)
            return if (next == state.sky) state else state.copy(sky = next)
        }
    }
}
