package si.lanisce.lani.app

import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import java.time.LocalDate

/**
 * What a whole reading of the node's content (AppViewModel.reload: the learner's state, the modules, the grammar book, the
 * scenes, the people, the clips' index …) was read for (docs/plans/05-built/nav-perf.md): the bridge's [version] of what
 * it serves then (GET /changes, companion/bridge/src/changes.ts: it moves with every file its answers come from, each
 * event, its day), the phone's [day] and [time] of day (the people's whereabouts are asked by it), the [pair], the
 * [learner] and the [culture] the answers were read for, and [at] when (SystemClock.elapsedRealtime, ms).
 *
 * Home coming back on view (a Back, its tile's way home) reads everything again only when a reading now would be for
 * something else ([sameAs] false): before, every Back made about 16 requests, the bridge worked a second on their answers
 * and the phone made 40 to 110 MB for the collector, for answers that hadn't changed.
 */
data class Fresh(
    val version: String,
    val day: LocalDate,
    val time: TimeOfDay,
    val pair: LangPair,
    val learner: Learner,
    val culture: String,
    val at: Long,
) {
    /**
     * Whether [now] reads the same as this did: the same version, day, time of day, pair, learner and culture, and no
     * more than [MAX_AGE_MS] later (what no version says: a remote town's news, a clock the bridge doesn't count by).
     */
    fun sameAs(now: Fresh): Boolean =
        version == now.version && day == now.day && time == now.time && pair == now.pair && learner == now.learner &&
            culture == now.culture && now.at - at in 0 until MAX_AGE_MS

    companion object {
        /** The longest a reading counts as fresh while nothing says otherwise: 10 minutes. */
        const val MAX_AGE_MS = 10 * 60_000L
    }
}
