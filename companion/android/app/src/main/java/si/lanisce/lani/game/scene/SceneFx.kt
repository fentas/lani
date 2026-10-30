package si.lanisce.lani.game.scene

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import si.lanisce.lani.game.GameState
import java.time.LocalDate

// What a scene's painter can show besides the weather (companion/SCENES.md, "Effects"): the oven burning in the
// kitchen, the lantern lit in the tent, birds flying up in the forest. A line or a reply sets levels for the effects
// it names ([DialogLine.fx]); each art's names are in [SceneArt.effects].

/** A line's effects: name → level 0..1, written as a number or true / false (1 / 0). */
object FxLevels : KSerializer<Map<String, Float>> {
    private val raw = MapSerializer(String.serializer(), JsonPrimitive.serializer())
    override val descriptor: SerialDescriptor = raw.descriptor

    override fun deserialize(decoder: Decoder): Map<String, Float> =
        raw.deserialize(decoder).mapValues { (_, v) -> (v.booleanOrNull?.let { if (it) 1f else 0f } ?: v.floatOrNull ?: 0f).coerceIn(0f, 1f) }

    override fun serialize(encoder: Encoder, value: Map<String, Float>) = raw.serialize(encoder, value.mapValues { JsonPrimitive(it.value) })
}

/**
 * The effects that play out in time rather than ease into a state, name → their span (s): the level rises at an even pace
 * over it from the cue, and a cue back down (or the dialog closing) takes it there at once ([FxEase]). The square's clock
 * striking the hour ([Strike]).
 */
val SLOW: Map<String, Double> = mapOf("strike" to Strike.SPAN)

/**
 * The square's clock striking the hour (the effect "strike", companion/SCENES.md "Arts"): as many strokes as the dialog counts
 * ([SceneFrame.count]: a count of the hour, [Counts]), else the hour the clock shows, one every [EVERY] s from [FIRST] s
 * after the cue, the level the time since the cue over [SPAN] (linear, [SLOW]). The painter swings the bell and sends a ring
 * out at each; [Sfx][si.lanisce.lani.game.ambient.Sfx] rings each stroke as the painter strikes it.
 */
object Strike {
    const val SPAN = 24.0
    const val FIRST = 0.5
    const val EVERY = 1.8

    /** The hour a clock showing [hour] (0 until 24, the scene's) has struck last: 1 … 12. */
    fun hour12(hour: Float): Int = ((hour.toInt() % 12 + 11) % 12) + 1

    /** When stroke [i] (from 0) rings, as a level of the effect. */
    fun at(i: Int): Float = ((FIRST + i * EVERY) / SPAN).toFloat()

    /**
     * Where the strike is at [level] for [n] strokes: the stroke ringing now (from 0) and how far into it (0 … 1 over
     * [EVERY] s), or null between none and after the last.
     */
    fun now(level: Float, n: Int): Pair<Int, Float>? {
        val s = level * SPAN - FIRST
        if (s < 0.0 || n <= 0) return null
        val i = (s / EVERY).toInt()
        if (i >= n) return null
        return i to ((s - i * EVERY) / EVERY).toFloat().coerceIn(0f, 1f)
    }
}

/** Whether a line or a reply to a right choice sets an effect (a reaction's pass with it, see [si.lanisce.lani.ui.scene.DialogRun.fx]). */
val Dialog.hasFx: Boolean get() = lines.any { l -> l.fx.isNotEmpty() || l.answers.any { it.reply?.fx.orEmpty().isNotEmpty() } }

/** Whether the effects the dialog ends with stay for the rest of the day: only when it says so ([Dialog.fxStays]). */
val Dialog.fxKept: Boolean get() = hasFx && fxStays == true

/** The effects over [base] once the dialog has come to line [at] with the learner's right [picks] (see [skyAt]). */
fun Dialog.fxAt(at: Int, picks: List<Int>, base: Map<String, Float> = emptyMap()): Map<String, Float> {
    val m = LinkedHashMap(base)
    played(at, picks) { _, fx, _ -> m.putAll(fx) }
    return m
}

/**
 * The effects on screen: each set one eases toward its level on its own (like the sky's, see [Fade]); one no longer
 * set eases out to 0 and is dropped, so the painter shows the place as it is by itself again (a lantern lit by the hour).
 */
data class FxEase(val fades: Map<String, Fade> = emptyMap(), val target: Map<String, Float> = emptyMap()) {
    fun toward(next: Map<String, Float>, now: Double): FxEase {
        val out = LinkedHashMap<String, Fade>()
        for ((name, f) in fades) {
            val to = next[name] ?: 0f
            if (name !in next && f.to == 0f && now - f.start >= f.span) continue // faded out a while ago
            // one that plays out in time goes back at once (the clock doesn't strike backwards), and on at its own pace
            out[name] = SLOW[name]?.let { span -> if (to < f.at(now)) Fade.still(to) else if (to == f.to) f else Fade(f.at(now), to, now, span, linear = true) }
                ?: f.toward(to, now)
        }
        for ((name, to) in next) if (name !in out) out[name] = SLOW[name]?.let { Fade(0f, to, now, it, linear = true) } ?: Fade(0f, to, now)
        return FxEase(out, next)
    }

    /** The levels at [now]: every effect set, and those let go until they have faded out. */
    fun at(now: Double): Map<String, Float> {
        if (fades.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Float>()
        for ((name, f) in fades) {
            if (name !in target && now - f.start >= f.span) continue
            out[name] = f.at(now)
        }
        return out
    }

    companion object {
        val NONE = FxEase()

        /** Resting at [levels]. */
        fun still(levels: Map<String, Float>) = FxEase(levels.mapValues { Fade.still(it.value) }, levels)
    }
}

/**
 * The effects finished dialogs left today (with `"fx_stays": true`: the lantern Luka lit), by scene id, kept in
 * [GameState.sceneFx] on the day [on] (ISO date), until midnight.
 */
@Serializable
data class DayFx(val on: String, val scenes: Map<String, Map<String, Float>> = emptyMap()) {
    companion object {
        /** Today's lasting effects in [scene]. */
        fun of(state: GameState?, today: LocalDate, scene: String): Map<String, Float> =
            state?.sceneFx?.takeIf { it.on == today.toString() }?.scenes?.get(scene).orEmpty()

        /** [state] once [dialog] of [scene] ended with the effects [end]: kept when the dialog keeps them. */
        fun keep(state: GameState, scene: String, dialog: Dialog, end: Map<String, Float>, today: LocalDate): GameState {
            if (!dialog.fxKept) return state
            val day = today.toString()
            val scenes = state.sceneFx?.takeIf { it.on == day }?.scenes.orEmpty()
            val next = DayFx(day, scenes + (scene to end))
            return if (next == state.sceneFx) state else state.copy(sceneFx = next)
        }
    }
}
