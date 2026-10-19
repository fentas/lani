package si.lanisce.lani.tutor.live

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The studio's work while the app isn't open (plan 5, "Background work on Android": "one step per work item, while
 * charging and on Wi-Fi (overnight). Android stopping the app only pauses it"): a job the learner waits for with the
 * network ([StudioWake.NOW]); a batch's poll every few minutes with the network ([StudioWake.BATCH]); the night's work
 * while charging on an unmetered network ([StudioWake.NIGHT]). Each run takes at most eight minutes (well under the
 * ten Android gives a job); a job saved after each step goes on at the next run. No expedited work, no foreground
 * service: nothing here is a call the learner sits and waits for (the open app runs those itself).
 */
class StudioWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val foreground = withContext(Dispatchers.Main) { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        if (foreground) return Result.success() // the open app runs the studio itself
        val live = TutorRuntime.live(applicationContext as Application, si.lanisce.lani.BuildConfig.QA, { k, a -> si.lanisce.lani.l10n.bi(k, *a.toList().toTypedArray()) })
        return try {
            live.studioBackground(BUDGET_MS)
            Result.success()
        } catch (e: Exception) {
            Result.success() // the next wake tries again
        }
    }

    companion object {
        private const val NOW = "lani-studio-now"
        private const val BATCH = "lani-studio-batch"
        private const val NIGHT = "lani-studio-night"
        private const val BUDGET_MS = 8 * 60_000L

        /** Work for [wake]: a run with the network now, a batch's poll in five minutes, the night's while charging on Wi-Fi. */
        fun schedule(ctx: Context, wake: StudioWake) {
            val wm = WorkManager.getInstance(ctx)
            when (wake) {
                StudioWake.NOW -> wm.enqueueUniqueWork(
                    NOW, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<StudioWork>().setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).build(),
                )
                StudioWake.BATCH -> wm.enqueueUniqueWork(
                    BATCH, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<StudioWork>().setInitialDelay(5, TimeUnit.MINUTES)
                        .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).build(),
                )
                StudioWake.NIGHT -> wm.enqueueUniqueWork(
                    NIGHT, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<StudioWork>()
                        .setConstraints(Constraints(requiredNetworkType = NetworkType.UNMETERED, requiresCharging = true)).build(),
                )
            }
        }

        /** Charging on an unmetered network now (the night's work may run while the app is open, too). */
        fun nightOk(ctx: Context): Boolean = runCatching {
            val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            charging && caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        }.getOrDefault(false)
    }
}
