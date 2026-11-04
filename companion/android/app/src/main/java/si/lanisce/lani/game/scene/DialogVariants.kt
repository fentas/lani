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
     *
     * By level (companion/SCENES.md, "Dialogs by level"): [rank] says each one's ([DialogLevels.rankOf]; the scenes come
     * trimmed to the learner's level, so the highest is their own). Those of the highest rank are asked first, a new one
     * or a review as above, then the next rank down, and so on: a lower level's dialog plays only on a day the learner's
     * own level has none to play. All of one rank (every happening before levels): as it always was.
     */
    fun pick(
        all: List<String>, playable: Collection<String>, heard: DialogsHeard?, today: LocalDate, dice: Float? = null,
        rank: (String) -> Int = { 0 },
    ): String? {
        val can = all.filter { it in playable }
        if (can.isEmpty()) return null
        if (all.size == 1) return can.single()
        val tiers = can.groupBy(rank).toSortedMap(compareByDescending { it }).values.toList()
        if (dice != null) {
            val top = tiers.first()
            return top[(dice * top.size).toInt().coerceIn(0, top.lastIndex)]
        }
        val h = heard ?: DialogsHeard()
        return tiers.firstNotNullOfOrNull { pickIn(it, h, today) }
    }

    /** Of [can] (one rank's, in order), the first not heard yet, else the review due ([pick]); null: none of them today. */
    private fun pickIn(can: List<String>, h: DialogsHeard, today: LocalDate): String? {
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
    fun play(a: ActiveHappening, state: GameState, today: LocalDate, hour: Int = java.time.LocalTime.now().hour): Pair<Happening, Dialog>? {
        val d = dialogOf(a) ?: return null
        val said = counted(a, state, today, hour)?.let { n -> Counts.render(d, n, a.scene.language) } ?: d
        return (d.memory?.let { a.happening.copy(memory = it) } ?: a.happening) to said
    }

    /**
     * The number happening [a]'s dialog of [today] counts in [state]'s village ([Counts.of], as [play] puts it into its
     * texts; a count of the hour by the scene's [hour]), null when it counts nothing: the picture draws that many of what is
     * counted (the sheep at the water, the clock's strokes).
     */
    fun counted(a: ActiveHappening, state: GameState, today: LocalDate, hour: Int = java.time.LocalTime.now().hour): Int? =
        dialogOf(a)?.let { d -> d.count?.let { c -> Counts.of(c, state, today, "${a.key}/${d.id}", hour) } }

    private fun dialogOf(a: ActiveHappening): Dialog? {
        val id = a.dialog ?: a.happening.dialog ?: return null
        return a.scene.dialogs.firstOrNull { it.id == id }
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
