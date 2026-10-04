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
            if (name !in next && f.to == 0f && now - f.start >= Sky.RAMP_S) continue // faded out a while ago
            out[name] = f.toward(to, now)
        }
        for ((name, to) in next) if (name !in out) out[name] = Fade(0f, to, now)
        return FxEase(out, next)
    }

    /** The levels at [now]: every effect set, and those let go until they have faded out. */
    fun at(now: Double): Map<String, Float> {
        if (fades.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Float>()
        for ((name, f) in fades) {
            if (name !in target && now - f.start >= Sky.RAMP_S) continue
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
