package si.lanisce.lani.road

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.MainActivity
import si.lanisce.lani.R
import si.lanisce.lani.data.ClipsApi
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangSetting
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import java.io.File
import java.util.Locale

/**
 * Getting the road ready in the background (companion/README.md, "Im Auto · In the car"): a foreground service (data
 * sync, a partial wake lock held) that gets the plan's files onto the phone ([RoadPrepWork]) with the screen off or the app
 * in the background. The app starts it once it has gathered the plan ([RoadPrep.start]), and again when it opens while a
 * plan is left: a run stopped halfway goes on where it stopped. Its notification, "🚗 Pripravljam za pot … 40 %", says
 * once the first block is on the phone that the sessions can start, and at the end what is on the phone.
 */
class RoadPrepService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var shown: Pair<Int, Boolean>? = null
    private var shownAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LangSetting(this).apply() // the labels in the learner's pair
        si.lanisce.lani.l10n.LearnerSetting(this).apply() // and the content said to the learner of the profile
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foreground()) {
            // not allowed now (restarted in the background): the app goes on with the plan when it opens
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) job = scope.launch { work() }
        return START_STICKY
    }

    /** Android 15: a data sync may run 6 hours a day. The plan stays; the app goes on with it when it opens. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        job?.cancel()
        stop()
    }

    override fun onDestroy() {
        scope.cancel()
        wake?.takeIf { it.isHeld }?.release()
        RoadPrepState.working = false
        if (RoadPrepState.progress is RoadProgress.Files) RoadPrepState.progress = null
        super.onDestroy()
    }

    private suspend fun work() {
        val store = RoadStore(this)
        val plan = withContext(Dispatchers.IO) { store.readPlan() }
        if (plan == null) return stop()
        RoadPrepState.working = true
        RoadPrepState.done = false
        RoadPrepState.problem = null
        wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lani:road-prep").apply {
            setReferenceCounted(false)
            acquire(WAKE_MS)
        }
        val base = plan.library.base
        val voice = RoadPrompts(this)
        var opened: Boolean? = null
        val api = runCatching { Prefs(this).config() }.getOrNull()?.let(::ClipsApi)
        try {
            val result = RoadPrepWork(
                store,
                fetch = { f -> fetch(store, plan, api, f) },
                render = { t ->
                    if (opened == null) opened = withContext(Dispatchers.Main) { voice.open(Locale.forLanguageTag(Lang.of(base)?.locale ?: base)) }
                    if (opened != true) throw RoadPrepWork.NoVoice()
                    voice.render(t, store.prompt(base, t))
                },
                progress = { p -> scope.launch { progressed(p) } },
                publish = { lib -> scope.launch { RoadPrepState.library = lib } },
            ).run(plan)
            withContext(Dispatchers.IO) { store.clearPlan() }
            RoadPrepState.library = result.library
            RoadPrepState.problem = when {
                result.noVoice -> bi("road.noVoice")
                result.clipsTried > 0 && result.clipsFailed == result.clipsTried -> bi("road.needsNode")
                else -> null
            }
            RoadPrepState.done = true
            Log.i(TAG, "ready: ${result.library.items.size} items, ${result.clipsFailed}/${result.clipsTried} clips and ${result.promptsFailed} prompts not had")
            finished(result.library)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // the plan stays: the app goes on with it when it opens again
            Log.w(TAG, "getting ready stopped", e)
            RoadPrepState.problem = bi("road.failed", "why" to (e.message ?: e.javaClass.simpleName))
            stop()
        } finally {
            voice.close()
        }
    }

    /** Clip [f] into the road's clips: from the phone's clip cache, else downloaded (`?count=0`: the node doesn't count it). */
    private suspend fun fetch(store: RoadStore, plan: RoadPlan, api: ClipsApi?, f: String): Boolean = withContext(Dispatchers.IO) {
        val to = store.clip(f)
        if (to.isFile) return@withContext true
        val part = File(store.clips, "$f.part")
        val cached = plan.cache?.let { File(it, f) }
        if (cached?.isFile == true) cached.copyTo(part, overwrite = true)
        else {
            val url = plan.urls[f] ?: return@withContext false
            (api ?: return@withContext false).download(url + (if ('?' in url) "&" else "?") + "count=0", part)
        }
        part.length() > 0 && part.renameTo(to)
    }

    private fun progressed(p: RoadProgress.Files) {
        if (!RoadPrepState.working) return
        RoadPrepState.progress = p
        // the notification at most once a second, and only when it changed
        val now = SystemClock.elapsedRealtime()
        val key = p.percent to p.playable
        if (key == shown || (now - shownAt < 1_000 && key.second == shown?.second)) return
        shown = key
        shownAt = now
        if (canNotify()) NotificationManagerCompat.from(this).notify(ID, notification(p))
    }

    private fun foreground(): Boolean = try {
        channel()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, ID, notification(RoadPrepState.progress as? RoadProgress.Files), type)
        true
    } catch (e: Exception) {
        Log.w(TAG, "can't get ready in the foreground now", e)
        false
    }

    /** Done: the notification says what is on the phone (a tap opens the app), and the service stops. */
    private fun finished(lib: RoadLibrary) {
        RoadPrepState.working = false
        RoadPrepState.progress = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        if (canNotify()) {
            val n = builder()
                .setContentTitle("🚗 ${inTarget("road.title")}")
                .setContentText(bi("road.done", "time" to RoadGather.duration(lib.seconds)))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(this).notify(ID, n)
        }
        stopSelf()
    }

    private fun stop() {
        RoadPrepState.working = false
        RoadPrepState.progress = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(p: RoadProgress.Files?): Notification {
        val percent = p?.percent ?: 0
        val text = when {
            p == null -> inBase("road.preparing")
            p.playable -> bi("road.playableNow", "time" to RoadGather.duration(RoadPrepState.library?.seconds ?: 0.0))
            else -> "${inBase("road.preparing")} · ${inBase("road.clips", "done" to p.clips, "total" to p.clipsTotal)} · ${inBase("road.prompts", "done" to p.prompts, "total" to p.promptsTotal)}"
        }
        return builder()
            .setContentTitle("🚗 ${inTarget("road.preparing")} … $percent %")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(100, percent, p == null)
            .setOngoing(true)
            .build()
    }

    private fun builder(): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this, ID, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "🚗 ${bi("road.title")}", NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        private const val TAG = "RoadPrep"
        private const val CHANNEL = "road-prep"
        private const val ID = 4201

        /** The wake lock's limit: longer than getting ready takes on a slow phone. */
        private const val WAKE_MS = 3 * 60 * 60 * 1000L

        /** Starts getting the plan's files (the app is on screen); false when Android didn't let it. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, RoadPrepService::class.java))
            true
        } catch (e: Exception) {
            Log.w(TAG, "can't start getting ready", e)
            false
        }
    }
}
