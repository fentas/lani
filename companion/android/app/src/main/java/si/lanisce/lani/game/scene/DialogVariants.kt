package si.lanisce.lani.game.scene

import kotlinx.serialization.Serializable
import si.lanisce.lani.game.GameState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the learner has heard of one happening's dialogs ([GameState.dialogsHeard], by "scene/happening"). */
@Serializable
data class DialogsHeard(
    /** Dialog id → the ISO date it was last heard to its end. */
    val heard: Map<String, String> = emptyMap(),
    /** The one heard last. */
    val last: String? = null,
)

/**
 * A happening's variants (companion/SCENES.md, "Variants: not the same dialog every time"): which of its dialogs plays
 * today, and what the learner has heard. Pure: the same village and day give the same variant all day long.
 */
object DialogVariants {
    /** Days before a variant heard comes back, once all are heard (a review). */
    const val GAP = 3L

    /** The dialogs [h] plays: its variants ([Happening.dialogs]), else its one [Happening.dialog]. */
    fun of(h: Happening): List<String> = h.dialogs.ifEmpty { listOfNotNull(h.dialog) }

    /**
     * Which of [all] (a happening's variants, in order) plays [today]: of those [playable] now (in the scene, naming only
     * whom the learner knows), the first one not [heard] yet; once all are heard, the one heard longest ago, if that was
     * at least [GAP] days ago and it isn't the one heard last (never the same twice in a row); else none, and the happening
     * rests today. A happening with one dialog plays it every time. [dice] (a visit: the host's record is the host's):
     * the day's dice picks among the playable ones instead.
     */
    fun pick(all: List<String>, playable: Collection<String>, heard: DialogsHeard?, today: LocalDate, dice: Float? = null): String? {
        val can = all.filter { it in playable }
        if (can.isEmpty()) return null
        if (all.size == 1) return can.single()
        if (dice != null) return can[(dice * can.size).toInt().coerceIn(0, can.lastIndex)]
        val h = heard ?: DialogsHeard()
        can.firstOrNull { it !in h.heard }?.let { return it }
        return can.asSequence()
            .filter { it != h.last }
            .mapNotNull { id -> runCatching { LocalDate.parse(h.heard.getValue(id)) }.getOrNull()?.let { id to it } }
            .filter { (_, on) -> ChronoUnit.DAYS.between(on, today) >= GAP }
            .minByOrNull { it.second }?.first
    }

    /**
     * What happening [a] plays [today] in [state]'s village: its dialog of today ([ActiveHappening.dialog], else its
     * [Happening.dialog]) with its number put in ([Counts]), and the happening with what its person remembers of it (a
     * variant's own [Dialog.memory]). Null: no such dialog in the scene.
     */
    fun play(a: ActiveHappening, state: GameState, today: LocalDate): Pair<Happening, Dialog>? {
        val id = a.dialog ?: a.happening.dialog ?: return null
        val d = a.scene.dialogs.firstOrNull { it.id == id } ?: return null
        val said = d.count?.let { c -> Counts.render(d, Counts.of(c, state, today, "${a.key}/${d.id}"), a.scene.language) } ?: d
        return (d.memory?.let { a.happening.copy(memory = it) } ?: a.happening) to said
    }

    /** [state] with [dialog] of happening [key] ("scene/happening") heard to its end [today]: the last one heard. */
    fun record(state: GameState, key: String, dialog: String, today: LocalDate): GameState {
        val was = state.dialogsHeard[key] ?: DialogsHeard()
        val now = DialogsHeard(was.heard + (dialog to today.toString()), dialog)
        return if (now == was) state else state.copy(dialogsHeard = state.dialogsHeard + (key to now))
    }

    /** Whether every one of [all] is in [heard]: time for the tutor to write a fresh one (the bridge asks). */
    fun allHeard(all: List<String>, heard: DialogsHeard?): Boolean = all.isNotEmpty() && heard != null && all.all { it in heard.heard }
}
