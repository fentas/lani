package si.lanisce.lani.audio

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import si.lanisce.lani.data.ClipIndex
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.ClipsApi
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.data.json
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangSetting
import si.lanisce.lani.road.RoadGather
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * The day's audio got ready on Wi-Fi (companion/README.md, "Voice": "Offline on the phone"): a background job (WorkManager)
 * that a few times a day, and when the app opens, gets the clips of the days ahead ([DayPlan], [PrefetchRun]) into the clip
 * cache, within the cap ([AudioCap]); and the offline voice ([OfflineVoices]) when the learner wants it. Only on an unmetered
 * network (Wi-Fi), with enough storage and battery; only while charging when the learner says so. "Prenesi zdaj · Download
 * now" in the settings runs it at once, on any network.
 */
object Prefetch {
    private const val PERIODIC = "lani-prefetch"
    private const val OPEN = "lani-prefetch-open"
    const val NOW = "lani-prefetch-now"

    /** A few times a day. */
    const val EVERY_HOURS = 6L

    /** When the app opens, the job runs again only if the last run was longer ago than this, or the plan is newer. */
    const val OPEN_AFTER_MS = 60 * 60_000L

    /** What the job waits for: pure, so the tests check it. [now]: the learner asked ("Prenesi zdaj"). */
    data class Rules(val unmetered: Boolean, val charging: Boolean, val batteryNotLow: Boolean, val storageNotLow: Boolean = true)

    fun rules(chargingOnly: Boolean, now: Boolean = false): Rules =
        if (now) Rules(unmetered = false, charging = false, batteryNotLow = false) else Rules(unmetered = true, charging = chargingOnly, batteryNotLow = true)

    fun constraints(r: Rules): Constraints = Constraints(
        requiredNetworkType = if (r.unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED,
        requiresCharging = r.charging,
        requiresBatteryNotLow = r.batteryNotLow,
        requiresStorageNotLow = r.storageNotLow,
    )

    fun periodic(settings: AudioSettings): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<PrefetchWorker>(EVERY_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints(rules(settings.chargingOnly)))
            .addTag(PERIODIC)
            .build()

    fun once(settings: AudioSettings, now: Boolean): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<PrefetchWorker>()
            .setConstraints(constraints(rules(settings.chargingOnly, now)))
            .setInputData(workDataOf(PrefetchWorker.NOW to now))
            .build()

    /** Whether the job has anything to do with these settings: the day's clips, or the offline voice. */
    fun wanted(settings: AudioSettings): Boolean = settings.cap.prefetch || settings.voiceWanted

    /** The periodic job as the settings say (again after they change: its constraints), or none. */
    fun schedule(context: Context) {
        val settings = AudioSettings(context)
        val wm = WorkManager.getInstance(context)
        if (!wanted(settings)) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic(settings))
    }

    /** The app opened: the job runs once on Wi-Fi, unless it ran lately for the same plan. */
    fun onOpen(context: Context) {
        val settings = AudioSettings(context)
        if (!wanted(settings)) return
        val files = AudioFiles(context.filesDir)
        val last = DayPlans.readStatus(files)
        val made = DayPlans.read(files)?.made ?: 0
        if (last != null && System.currentTimeMillis() - last.at < OPEN_AFTER_MS && last.at >= made) return
        WorkManager.getInstance(context).enqueueUniqueWork(OPEN, ExistingWorkPolicy.KEEP, once(settings, now = false))
    }

    /** "⬇️ Prenesi zdaj · Download now": at once, on any network. */
    fun now(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, once(AudioSettings(context), now = true))
    }
}

/** The day's plan and the last prefetch's status, in audio/ ([AudioFiles.dir]). */
object DayPlans {
    private fun planFile(files: AudioFiles) = File(files.dir, "day.json")
    private fun statusFile(files: AudioFiles) = File(files.dir, "status.json")

    fun read(files: AudioFiles): DayPlan? =
        runCatching { json.decodeFromString(DayPlan.serializer(), planFile(files).readText()) }.getOrNull()

    fun write(files: AudioFiles, plan: DayPlan) = write(planFile(files), json.encodeToString(DayPlan.serializer(), plan))

    fun readStatus(files: AudioFiles): PrefetchStatus? =
        runCatching { json.decodeFromString(PrefetchStatus.serializer(), statusFile(files).readText()) }.getOrNull()

    fun writeStatus(files: AudioFiles, s: PrefetchStatus) = write(statusFile(files), json.encodeToString(PrefetchStatus.serializer(), s))

    private fun write(f: File, raw: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(raw)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    /**
     * The days to get ready from [plan] on [today], with the due cards as the node has them now ([cards], GET /state's, with
     * their due dates): today's and tomorrow's lists, their cards replaced, so the tutor's new cards come too. Without a plan
     * for today (the app wasn't opened lately), the cards alone, in the narrator's voice.
     */
    fun days(plan: DayPlan?, today: LocalDate, cards: List<si.lanisce.lani.data.ReviewCard>?): List<DayList> {
        val ahead = plan?.from(today).orEmpty().take(2)
        val lists = ahead.ifEmpty { listOf(DayList(today.toString(), emptyList())) } .let { l ->
            if (l.size < 2) l + DayList(today.plusDays(1).toString(), emptyList(), l.first().companion) else l
        }
        if (cards == null) return lists
        return lists.mapIndexed { i, d ->
            val day = LocalDate.parse(d.day)
            val fresh = DayAudio.cards(cards, day, first = i == 0, voices = d.companion)
            DayAudio.list(day, listOf(fresh, d.wants.filter { it.why != "card" }), d.companion)
        }
    }
}

/** The background job: see [Prefetch]. */
class PrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        LangSetting(app).apply() // the target language: the offline voice's
        val settings = AudioSettings(app)
        val config = Prefs(app).config() ?: return Result.success()
        val files = AudioFiles(app.filesDir)
        val api = ClipsApi(config)
        if (settings.cap.prefetch) {
            val status = try {
                prefetch(files, config, api, settings.cap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "prefetch failed", e)
                PrefetchStatus(at = System.currentTimeMillis(), day = LocalDate.now().toString(), problem = e.message ?: e.javaClass.simpleName)
            }
            DayPlans.writeStatus(files, status)
            Log.i(TAG, "prefetch: $status")
        }
        if (settings.voiceWanted) {
            try {
                // the learner's own target language (a visit's town has its own, the app open on one meanwhile)
                OfflineVoices(app.filesDir).install(L10n.ownPair.target.code, OfflineVoiceApi(config)) { done, total ->
                    setProgress(workDataOf(PROGRESS_DONE to done, PROGRESS_TOTAL to total))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "offline voice not got", e)
            }
        }
        return Result.success()
    }

    private suspend fun prefetch(files: AudioFiles, config: si.lanisce.lani.data.BridgeConfig, api: ClipsApi, cap: AudioCap): PrefetchStatus {
        val today = LocalDate.now()
        // the due cards as the node has them now (the tutor adds some in a session), else the plan's
        val cards = runCatching {
            val raw = si.lanisce.lani.data.Bridge(config).stateRaw()
            RoadGather.cards(raw, today.plusDays(1).toString()).map { (c, due) -> c.copy(due = due?.let(LocalDate::parse)) }
        }.getOrNull()
        val days = DayPlans.days(DayPlans.read(files), today, cards)
        val raw = api.index()
        val index = Clips.parseIndex(raw)
        val run = PrefetchRun(files, cap, index, prepare = { api.prepare(it) }, download = { url, to -> api.download(url, to) })
        val status = run.run(days)
        // the app's index (the clip cache's copy) with what the node voiced meanwhile: those play offline too
        runCatching { File(files.clips, Clips.INDEX).writeText(if (run.updated === index) raw else encodeIndex(run.updated)) }
        return status
    }

    companion object {
        private const val TAG = "Prefetch"
        const val NOW = "now"
        const val PROGRESS_DONE = "done"
        const val PROGRESS_TOTAL = "total"

        private val INDEX = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

        fun encodeIndex(index: ClipIndex): String = json.encodeToString(INDEX, index)
    }
}
