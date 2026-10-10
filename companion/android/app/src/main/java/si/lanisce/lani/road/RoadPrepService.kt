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
import si.lanisce.lani.audio.AudioBudget
import si.lanisce.lani.audio.AudioFiles
import si.lanisce.lani.audio.AudioSettings
import si.lanisce.lani.audio.ClipFiles
import si.lanisce.lani.audio.Piper
import si.lanisce.lani.audio.Room
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
        val target = plan.library.target
        val voice = RoadPrompts(this)
        var opened: Boolean? = null
        // the quiz's target-language texts the node can't voice: the phone's own voice in the target language, if it has one
        val own = RoadPrompts(this)
        var ownOpened: Boolean? = null
        val api = runCatching { Prefs(this).config() }.getOrNull()?.let(::ClipsApi)
        var prepares = api != null
        // the car's library counts in the learner's cap (si.lanisce.lani.audio.AudioCap), after the day's clips: what doesn't
        // fit is left out, as what can't be had
        val files = AudioFiles(filesDir)
        val room = withContext(Dispatchers.IO) { roadRoom(files) }
        // the quiz's target-language texts the node can't voice: the phone's offline voice first (Piper), if it has one
        val piper = Piper(this).also { it.target = target }
        try {
            val result = RoadPrepWork(
                store,
                fetch = { f -> fetch(store, plan, api, f, files, room) },
                render = { t ->
                    if (opened == null) opened = withContext(Dispatchers.Main) { voice.open(Locale.forLanguageTag(Lang.of(base)?.locale ?: base)) }
                    if (opened != true) throw RoadPrepWork.NoVoice()
                    val to = store.prompt(base, t)
                    voice.render(t, to) && fits(room, to)
                },
                progress = { p -> scope.launch { progressed(p) } },
                publish = { lib -> scope.launch { RoadPrepState.library = lib } },
                speak = { texts ->
                    // the node first (within its quota, not as a live line), each of its clips downloaded as the others are
                    val urls = if (!prepares) null else try {
                        withContext(Dispatchers.IO) { api?.prepare(texts.map { it.text to it.voice }) }.also { if (it == null) prepares = false }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    texts.mapIndexed { i, s ->
                        val to = store.spoken(s.voice, s.text)
                        val got = urls?.getOrNull(i)?.let { u -> runCatching { download(store, api, u, to) }.getOrDefault(false) } == true
                        val had = got || runCatching { piper.render(s.text, 1f) }.getOrNull()?.let { wav ->
                            withContext(Dispatchers.IO) { runCatching { wav.copyTo(to, overwrite = true); true }.getOrDefault(false) }
                        } == true || run {
                            if (ownOpened == null) ownOpened = withContext(Dispatchers.Main) { own.open(Locale.forLanguageTag(Lang.of(target)?.locale ?: target)) }
                            ownOpened == true && own.render(s.text, to)
                        }
                        had && fits(room, to)
                    }
                },
            ).run(plan)
            withContext(Dispatchers.IO) {
                store.clearPlan()
                // the library grew: the clip cache makes room under the cap (the least recently played clips go)
                files.forgetRoad()
                runCatching { files.trim(AudioSettings(this@RoadPrepService).cap) }
            }
            RoadPrepState.library = result.library
            RoadPrepState.problem = when {
                result.noVoice -> bi("road.noVoice")
                result.clipsTried > 0 && result.clipsFailed == result.clipsTried && !room.full -> bi("road.needsNode")
                room.full -> bi("road.cap")
                else -> null
            }
            RoadPrepState.done = true
            Log.i(
                TAG,
                "ready: ${result.library.items.size} items, ${result.clipsFailed}/${result.clipsTried} clips, ${result.promptsFailed} prompts and " +
                    "${result.spokenFailed} quiz texts not had",
            )
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
            own.close()
            piper.release()
        }
    }

    /**
     * What the car's library may still take under the learner's cap ([AudioBudget.room]): the cap less the library and the
     * day's clips. No limit with the cap off or unlimited (as before).
     */
    private fun roadRoom(files: AudioFiles): Room {
        val cap = AudioSettings(this).cap
        val limit = AudioBudget.limit(cap)?.takeIf { !AudioBudget.cacheOnly(cap) } ?: return Room(null)
        files.forgetRoad()
        val road = files.roadFiles(maxAgeMs = 0)
        return Room(AudioBudget.room(files.cacheFiles(ids = true), road, limit, files.keep()))
    }

    /** [f], just got for the library, fits under the cap: counted; else it goes again (and the item is left out). */
    private fun fits(room: Room, f: File): Boolean {
        if (room.take(f.length())) return true
        f.delete()
        return false
    }

    /** A clip of the node's at [url] into [to] (`?count=0`: getting ready isn't a request to count). */
    private suspend fun download(store: RoadStore, api: ClipsApi?, url: String, to: File): Boolean = withContext(Dispatchers.IO) {
        if (to.isFile) return@withContext true
        val part = File(store.spoken, "${to.name}.part")
        (api ?: return@withContext false).download(url + (if ('?' in url) "&" else "?") + "count=0", part)
        part.length() > 0 && part.renameTo(to)
    }

    /**
     * Clip [f] into the road's clips: linked from the phone's clip cache when it has it (one file, nothing copied), else
     * downloaded (`?count=0`: the node doesn't count it), once: a clip the day's prefetch is getting meanwhile is waited for
     * ([ClipFiles]). Within the cap ([room]).
     */
    private suspend fun fetch(store: RoadStore, plan: RoadPlan, api: ClipsApi?, f: String, files: AudioFiles, room: Room): Boolean = withContext(Dispatchers.IO) {
        val to = store.clip(f)
        if (to.isFile) return@withContext true
        val url = plan.urls[f]
        val from = listOfNotNull(plan.cache?.let { File(it, f) }, File(files.clips, f))
        val got = ClipFiles.get(to, from) { into ->
            if (url == null || api == null) throw java.io.IOException("no clip")
            api.download(url + (if ('?' in url) "&" else "?") + "count=0", into)
        }
        got && fits(room, to)
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
