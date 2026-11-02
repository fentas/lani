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
 * the [culture] the answers were read for, [at] when everything was last read whole and [asked] when what no version says
 * was last read (SystemClock.elapsedRealtime, ms).
 *
 * Home coming back on view (a Back, its tile's way home) reads nothing again while nothing moved ([sameAs]); after a real
 * change, only what depends on the areas that moved, while the reading is for the same day, time, pair, learner and
 * culture and the last whole one is under an hour old ([within]); else everything. What no version says (a linked town's
 * news, the voice store's clips) is read again every ten minutes, with what moved ([due]; docs/plans/05-built/nav-perf-2.md).
 * Before: every Back read everything (about 16 requests, a second of the bridge's work, 40 to 110 MB for the phone's
 * collector), then every Back after any change, then every Back ten minutes on.
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
    val asked: Long = at,
) {
    /** Whether [now] reads the same as this did: the same version, [within], and what no version says not [due]. */
    fun sameAs(now: Fresh): Boolean = version == now.version && within(now) && !due(now)

    /**
     * Whether [now] is for what this was read for (the same day, time of day, pair, learner and culture) no more than
     * [WHOLE_AGE_MS] after the last whole reading (a clock the bridge doesn't count by, should a read have one): then only
     * the reads whose areas moved are read again, when the bridge says its areas.
     */
    fun within(now: Fresh): Boolean = unlike(now) == null

    /**
     * Whether what no version says (a remote town's news, the voice store's clips; AppViewModel's NO_VERSION) was last read
     * [MAX_AGE_MS] or more before [now]: read again then, whether anything moved or not.
     */
    fun due(now: Fresh): Boolean = now.at - asked !in 0 until MAX_AGE_MS

    /** Why [now] isn't [within] what this was read for (the first that differs, for the log); null when it is. */
    fun unlike(now: Fresh): String? = when {
        day != now.day -> "another day"
        time != now.time -> "another time of day"
        pair != now.pair -> "another pair"
        learner != now.learner -> "another learner"
        culture != now.culture -> "another culture"
        now.at - at !in 0 until WHOLE_AGE_MS -> "an hour after the last whole reading"
        else -> null
    }

    companion object {
        /** The longest what no version says counts as read: 10 minutes. */
        const val MAX_AGE_MS = 10 * 60_000L

        /** The longest everything counts as read while nothing says otherwise: an hour. */
        const val WHOLE_AGE_MS = 60 * 60_000L
    }
}
