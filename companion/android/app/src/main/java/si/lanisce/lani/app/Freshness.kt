package si.lanisce.lani.app

import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import java.time.LocalDate

/**
 * What a reading of the node's content (AppViewModel.reload: the learner's state, the modules, the grammar book, the
 * scenes, the people, the clips' index …) was read for (docs/plans/05-built/nav-perf.md, changes-areas.md): the bridge's
 * [version] of what it serves then (GET /changes, companion/bridge/src/changes.ts: it moves with every file its answers
 * come from, its day) and of each of its [areas] (the learner's own data, the village, the towns, the family's, the
 * content), the phone's [day] and [time] of day (the people's whereabouts are asked by it), the [pair], the [learner] and
 * the [culture] the answers were read for, and [at] when everything was last read whole (SystemClock.elapsedRealtime, ms).
 *
 * Home coming back on view (a Back, its tile's way home) reads nothing again while nothing moved ([sameAs]); after a real
 * change, only what depends on the areas that moved, while the reading is for the same day, time, pair, learner and
 * culture and not too old ([within]); else everything. Before: every Back read everything (about 16 requests, a second of
 * the bridge's work, 40 to 110 MB for the phone's collector), then every Back after any change.
 */
data class Fresh(
    val version: String,
    val day: LocalDate,
    val time: TimeOfDay,
    val pair: LangPair,
    val learner: Learner,
    val culture: String,
    val at: Long,
    val areas: Map<String, String> = emptyMap(),
) {
    /** Whether [now] reads the same as this did: the same version, and [within]. */
    fun sameAs(now: Fresh): Boolean = version == now.version && within(now)

    /**
     * Whether [now] is for what this was read for (the same day, time of day, pair, learner and culture) no more than
     * [MAX_AGE_MS] after the last whole reading (what no version says: a remote town's news, a clock the bridge doesn't
     * count by): then only the reads whose areas moved are read again, when the bridge says its areas.
     */
    fun within(now: Fresh): Boolean = unlike(now) == null

    /** Why [now] isn't [within] what this was read for (the first that differs, for the log); null when it is. */
    fun unlike(now: Fresh): String? = when {
        day != now.day -> "another day"
        time != now.time -> "another time of day"
        pair != now.pair -> "another pair"
        learner != now.learner -> "another learner"
        culture != now.culture -> "another culture"
        now.at - at !in 0 until MAX_AGE_MS -> "ten minutes after the last whole reading"
        else -> null
    }

    companion object {
        /** The longest everything counts as read while nothing says otherwise: 10 minutes. */
        const val MAX_AGE_MS = 10 * 60_000L
    }
}
