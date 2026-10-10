package si.lanisce.lani.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.audio.AudioCap
import si.lanisce.lani.audio.AudioFiles
import si.lanisce.lani.audio.AudioSettings
import si.lanisce.lani.audio.DayPlans
import si.lanisce.lani.audio.OfflineVoiceApi
import si.lanisce.lani.audio.OfflineVoiceInfo
import si.lanisce.lani.audio.OfflineVoiceWorker
import si.lanisce.lani.audio.OfflineVoices
import si.lanisce.lani.audio.Piper
import si.lanisce.lani.audio.Prefetch
import si.lanisce.lani.audio.PrefetchStatus
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.l10n.L10n

/**
 * "🔊 Zvok brez povezave · Offline audio" (companion/README.md, "Voice": "Offline on the phone"): the cap, "only while
 * charging", the offline voice (its download, size, removal), what's on the phone now, "Prenesi zdaj · Download now"; the
 * day's plan the background job gets ready ([DayAudioGather], [Prefetch]); and the offer of the offline voice after pairing.
 * Compose state, written on the main thread.
 */
class OfflineAudioController(
    context: Context,
    private val scope: CoroutineScope,
    private val config: suspend () -> BridgeConfig?,
) {
    private val app = context.applicationContext
    val settings = AudioSettings(app)
    val files = AudioFiles(app.filesDir)
    val voices = OfflineVoices(app.filesDir)

    /** The phone's offline voice, for [si.lanisce.lani.data.Speaker] (the target language's, when it's on the phone). */
    val piper = Piper(app, voices)

    var cap by mutableStateOf(settings.cap)
        private set
    var chargingOnly by mutableStateOf(settings.chargingOnly)
        private set

    /** Lani's audio on the phone now (null until counted). */
    var usage by mutableStateOf<AudioFiles.Usage?>(null)
        private set

    /** What the last prefetch did. */
    var status by mutableStateOf<PrefetchStatus?>(null)
        private set

    /** The offline voice on the phone (the target language's), or null. */
    var voice by mutableStateOf<OfflineVoiceInfo?>(null)
        private set

    /** The offline voice the node offers for the target language (its name, size, licence), or null (an older node, none). */
    var offered by mutableStateOf<OfflineVoiceInfo?>(null)
        private set

    /** The voice is on its way: bytes done of all (0 of 0 while it starts). */
    var voiceProgress by mutableStateOf<Pair<Long, Long>?>(null)
        private set

    /** Why the last download of the voice failed. */
    var voiceProblem by mutableStateOf<String?>(null)
        private set

    /** The day's clips are being got now. */
    var prefetching by mutableStateOf(false)
        private set

    /** After pairing: "Prenesi glas brez povezave? · Download the offline voice?" shows (once). */
    var offer by mutableStateOf(false)
        private set

    private val target: String get() = L10n.ownPair.target.code

    init {
        val wm = WorkManager.getInstance(app)
        scope.launch {
            wm.getWorkInfosForUniqueWorkFlow(OfflineVoiceWorker.NAME).collect { infos ->
                val w = infos.firstOrNull() ?: return@collect
                when (w.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                        voiceProgress = w.progress.getLong(OfflineVoiceWorker.DONE, 0) to w.progress.getLong(OfflineVoiceWorker.TOTAL, 0)
                    WorkInfo.State.FAILED -> {
                        voiceProgress = null
                        voiceProblem = w.outputData.getString(OfflineVoiceWorker.ERROR)
                        load()
                    }
                    else -> {
                        voiceProgress = null
                        load()
                    }
                }
            }
        }
        scope.launch {
            wm.getWorkInfosForUniqueWorkFlow(Prefetch.NOW).collect { infos ->
                val running = infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                if (prefetching && !running) load()
                prefetching = running
            }
        }
        load()
        Prefetch.schedule(app)
    }

    /** Reads what's on the phone again (the sizes, the last prefetch, the voice). */
    fun load() {
        scope.launch {
            val (u, s, v) = withContext(Dispatchers.IO) {
                piper.refresh()
                Triple(runCatching { files.usage() }.getOrNull(), DayPlans.readStatus(files), voices.installed(target))
            }
            usage = u
            status = s
            voice = v
        }
    }

    /** Asks the node which offline voice it has for the target language (its size and licence, for the settings). */
    fun loadOffer() {
        scope.launch {
            val c = config() ?: return@launch
            offered = withContext(Dispatchers.IO) {
                runCatching { OfflineVoiceApi(c).list()?.firstOrNull { it.language == target } }.getOrNull()
            }
        }
    }

    fun choose(c: AudioCap) {
        settings.cap = c
        cap = c
        Prefetch.schedule(app)
        scope.launch {
            // a smaller cap: the least recently played clips go now
            withContext(Dispatchers.IO) { runCatching { files.trim(c) } }
            load()
        }
    }

    fun chargeOnly(on: Boolean) {
        settings.chargingOnly = on
        chargingOnly = on
        Prefetch.schedule(app)
    }

    /** "⬇️ Prenesi glas · Download the voice": on its way in the background; the prefetch gets it again should it go. */
    fun downloadVoice() {
        settings.voiceWanted = true
        settings.voiceOffered = true
        offer = false
        voiceProblem = null
        voiceProgress = 0L to 0L
        OfflineVoiceWorker.start(app, target)
        Prefetch.schedule(app)
    }

    /** "🗑️ Odstrani · Remove": the voice off the phone; the phone's text-to-speech speaks offline again. */
    fun removeVoice() {
        settings.voiceWanted = false
        WorkManager.getInstance(app).cancelUniqueWork(OfflineVoiceWorker.NAME)
        scope.launch {
            withContext(Dispatchers.IO) { voices.remove(target) }
            Prefetch.schedule(app)
            load()
        }
    }

    /** "⬇️ Prenesi zdaj · Download now": the day's clips at once, on any network. */
    fun downloadNow() {
        prefetching = true
        Prefetch.now(app)
    }

    /** QA's "audio:prefetch" (a debug build): the day's plan made now, then the job at once. */
    fun planAndFetch(gather: () -> DayAudioGather?) = plan(gather, force = true) { downloadNow() }

    /** The offer after pairing was answered "Ne zdaj · Not now": not asked again (the settings have it). */
    fun declineOffer() {
        settings.voiceOffered = true
        offer = false
    }

    /**
     * A phone just paired: the offline voice is offered once, when the node has one for the target language and it isn't on
     * the phone yet.
     */
    fun afterPairing() {
        if (settings.voiceOffered) return
        scope.launch {
            val c = config() ?: return@launch
            val o = withContext(Dispatchers.IO) { runCatching { OfflineVoiceApi(c).list()?.firstOrNull { it.language == target } }.getOrNull() }
            offered = o
            if (o != null && voices.installed(target) == null) offer = true
        }
    }

    /** The plan made last (when), so it isn't made again at every refresh. */
    private var planned = 0L
    private var plannedDay = ""

    /**
     * Writes the days ahead ([DayAudioGather]) for the background job and asks it to run on Wi-Fi ([Prefetch.onOpen]); at most
     * every [PLAN_EVERY_MS], unless the day changed. Not with the cap off, nor on a visit (the town's language isn't the
     * learner's).
     */
    fun plan(gather: () -> DayAudioGather?, force: Boolean = false, then: () -> Unit = { Prefetch.onOpen(app) }) {
        if (!settings.cap.prefetch) return
        val now = System.currentTimeMillis()
        val day = java.time.LocalDate.now().toString()
        if (!force && day == plannedDay && now - planned < PLAN_EVERY_MS) return
        val g = gather() ?: return
        planned = now
        plannedDay = day
        scope.launch {
            try {
                val plan = withContext(Dispatchers.Default) { g.plan() } // the village's day, hour by hour: off the main thread
                withContext(Dispatchers.IO) { DayPlans.write(files, plan) }
                android.util.Log.i("OfflineAudio", "the day's plan: ${plan.days.joinToString { "${it.day} ${it.wants.size} lines" }}")
                then()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("OfflineAudio", "the day's plan failed", e)
                planned = 0
            }
        }
    }

    companion object {
        /** The day's plan is made again at most this often while the app is open (each refresh would otherwise). */
        const val PLAN_EVERY_MS = 20 * 60_000L
    }
}
