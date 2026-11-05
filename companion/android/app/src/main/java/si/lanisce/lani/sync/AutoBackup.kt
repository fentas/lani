package si.lanisce.lani.sync

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * What the automatic backup did last (<filesDir>/lani/sync/backup.json; docs/plans/05-built/last-small.md, "The app's backup").
 * Apart from the settings: the backup runs as WorkManager work ([BackupWork]), in a DataSync of its own, while the app's holds
 * its settings in memory. Times are ISO instants.
 */
@Serializable
data class BackupRecord(
    /** The last automatic try. */
    val lastTry: String? = null,
    /** The last automatic try that failed, and why ([BackupProblem]'s name); null once one succeeds. */
    val failedAt: String? = null,
    val problem: String? = null,
    /** Failed tries in a row: the backoff's step. */
    val failures: Int = 0,
    /** The data's digest at the last backup that went up ([BackupArchive.digest]): the same data isn't sent again. */
    val digest: String? = null,
    /** When the data was last found as it was at that backup: it counts as backed up then. */
    val checkedAt: String? = null,
) {
    val failedProblem: BackupProblem? get() = problem?.let { runCatching { BackupProblem.valueOf(it) }.getOrNull() }
}

/** The next automatic backup: after [delayMs], on any network or ([anyNetwork] false) an unmetered one only. */
data class BackupPlan(val delayMs: Long, val anyNetwork: Boolean)

/**
 * When the automatic backup runs. Once a day; after a failure it waits 1 h, then 2, 4, 8, 16, and from then on a day, so a
 * failing backup tries at most once a day. On an unmetered network, unless the last good backup is older than a week (or there
 * is none), when any network will do. "💾 Back up now" goes on any network, whenever asked.
 */
object AutoBackupPolicy {
    val EVERY: Duration = Duration.ofHours(20)
    val FIRST_BACKOFF: Duration = Duration.ofHours(1)
    val MAX_BACKOFF: Duration = Duration.ofDays(1)
    val METERED_AFTER: Duration = Duration.ofDays(7)

    /** How long to wait after the [failures]th failure in a row. */
    fun backoff(failures: Int): Duration =
        if (failures <= 0) Duration.ZERO else minOf(FIRST_BACKOFF.multipliedBy(1L shl (failures - 1).coerceAtMost(20)), MAX_BACKOFF)

    /** The last time the relay held the data as it is: the last backup, or a later check that found the data unchanged. */
    fun lastGood(lastBackup: Instant?, record: BackupRecord): Instant? = listOfNotNull(lastBackup, record.checkedAt?.let(::instant)).maxOrNull()

    /**
     * The next automatic backup at [now], or null when none is due: the last good one is less than [EVERY] old and nothing failed
     * since (the next trip to the background after that asks again).
     */
    fun plan(lastBackup: Instant?, record: BackupRecord, now: Instant): BackupPlan? {
        val good = lastGood(lastBackup, record)
        val failed = record.failedAt?.let(::instant)
        val anyNetwork = good == null || Duration.between(good, now) > METERED_AFTER
        if (failed != null && record.failures > 0) {
            val at = maxOf(failed.plus(backoff(record.failures)), good?.plus(EVERY) ?: Instant.EPOCH)
            return BackupPlan(Duration.between(now, at).toMillis().coerceAtLeast(0), anyNetwork)
        }
        if (good != null && Duration.between(good, now) < EVERY) return null
        return BackupPlan(0, anyNetwork)
    }

    private fun instant(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()
}
