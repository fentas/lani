package si.lanisce.lani.sync

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import si.lanisce.lani.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The automatic backup as WorkManager work (docs/plans/05-built/last-small.md, "The app's backup"): it outlives the app's process,
 * waits for its network, and runs in a DataSync of its own ([appDataSync]), which reads and writes the same files. What it does and
 * when: [DataSync.runAutoBackup], [AutoBackupPolicy].
 */
class BackupWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val outcome = runCatching { appDataSync(applicationContext, scope).runAutoBackup() }
            Log.i("DataSync", "backup work: ${outcome.getOrElse { "failed: ${it.message}" }}")
        } finally {
            scope.cancel()
        }
        // a failure plans its own next try, after its backoff: never WorkManager's retry, whose backoff stops at five hours
        return Result.success()
    }

    companion object {
        const val NAME = "lani-backup"

        /** The work for [plan]: after its delay, on an unmetered network unless any will do. */
        fun request(plan: BackupPlan): OneTimeWorkRequest = OneTimeWorkRequestBuilder<BackupWork>()
            .setInitialDelay(plan.delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (plan.anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
            .build()

        /**
         * Plans the next automatic backup. From the app going to the background ([fromWork] false), no backup runs in the process
         * ([DataSync.running]), so the plan replaces one that waits: it is worked out from the record each time, so a backoff stays.
         * From a backup that just failed, it comes after that one, which is still running.
         */
        fun enqueue(context: Context, plan: BackupPlan, fromWork: Boolean) {
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, if (fromWork) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request(plan))
        }
    }
}

/** The app's DataSync: the ViewModel's and a [BackupWork]'s are built alike. */
fun appDataSync(app: Context, scope: CoroutineScope): DataSync = DataSync(
    File(app.filesDir, "lani"), scope, KeystoreBox(app),
    deviceName = Build.MODEL ?: "Android",
    clientId = BuildConfig.GITHUB_CLIENT_ID,
    relayUrl = BuildConfig.RELAY_URL,
    appVersion = "${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME}",
    log = { Log.i("DataSync", it) },
    scheduleBackup = { plan, fromWork -> BackupWork.enqueue(app, plan, fromWork) },
)
