package si.lanisce.lani.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.data.AudioPlan
import si.lanisce.lani.data.AttestState
import si.lanisce.lani.data.AudioTrial
import si.lanisce.lani.data.Enrolled
import si.lanisce.lani.data.TrialApi
import si.lanisce.lani.data.TrialAnswer

/**
 * The audio trial for the screens ([AudioTrial]; data/AudioPlan.kt): it starts in the background as soon as the learner is on
 * this device and online, so the first moment's voices are already the village's; a failure or being offline changes nothing
 * and it is tried again later, without a word to the learner. How it stands is read at most once a day ([AudioTrial.ask]). A
 * node's learner is never asked about ([hosted]), and with no voice service ([glas]) nothing is sent.
 *
 * [voicesChanged]: the trial began or ended, so the clips' index is read again from who answers now. Compose state, on the
 * main thread; a background job's 403 comes through [AudioPlan.trial].
 */
class AudioTrialController(
    context: Context,
    private val scope: CoroutineScope,
    private val hosted: () -> Boolean,
    private val glas: () -> String = { AudioPlan.glas },
    private val voicesChanged: () -> Unit,
) {
    var state by mutableStateOf(AudioTrial())
        private set

    /** The app's own files: where the QA script leaves the QA key for a moment. */
    private val files = context.applicationContext.filesDir

    /** An ask on its way, and when the last one left: a failed one isn't tried again before [RETRY_MS], or the network's back. */
    private var asking = false
    private var askedAt = 0L

    init {
        AudioPlan.open(context)
        state = AudioPlan.trial.value
        scope.launch {
            AudioPlan.trial.collect { t ->
                val changed = t.status != state.status
                state = t
                if (BuildConfig.QA) Log.i(TAG, "status=${t.status} days=${t.days} ends=${t.endsAt} startSeen=${t.startSeen} endSeen=${t.endSeen}")
                if (changed) voicesChanged()
            }
        }
    }

    /** There is a voice service and this device's learner may use it. */
    private val offered: Boolean get() = glas().isNotBlank() && hosted()

    /** The card to show on Home now: once each, none on a node or without a voice service. */
    val card: AudioTrial.Card? get() = state.card(offered, System.currentTimeMillis())

    /** The settings' line: it began or ended here, on this device's learner. */
    val said: Boolean get() = offered && state.said

    /** [c] was closed: never again. */
    fun seen(c: AudioTrial.Card) = AudioPlan.change { it.seen(c) }

    /**
     * Starts the trial or reads how it stands when that's due ([AudioTrial.ask]), and before a start (or when it is due) enrolls
     * this install's key with the voice service (data/GlasAttest.kt): attested, the trial is every voice's; refused (an
     * emulator, an unlocked or old phone), the library's only, with no card; failing for a passing reason (no network, remote
     * keys not provisioned yet), nothing starts and it is tried again later, more slowly, so the trial isn't used up. Nothing is
     * said to the learner. The learner came to this device, the app came on screen; [again]: the network came back, so a
     * failed ask needn't wait.
     */
    fun check(again: Boolean = false) {
        if (!offered) return
        val now = System.currentTimeMillis()
        AudioPlan.change { it.settled(now).withId() }
        val t = AudioPlan.trial.value
        val ask = t.ask(now)
        // a key is enrolled while there is a trial to start or to run, not after it ended
        val due = if (t.status == AudioTrial.ENDED) null else AudioPlan.attest.value.due(now)
        if (ask == null && due == null) return
        if (asking || (!again && now - askedAt < RETRY_MS)) return
        asking = true
        askedAt = now
        val api = TrialApi(glas(), t.id)
        scope.launch {
            val a: TrialAnswer? = withContext(Dispatchers.IO) {
                runCatching {
                    if (due != null) verdict(AudioPlan.enroll(renew = due == AttestState.Due.RENEW))
                    val attest = AudioPlan.attest.value
                    when {
                        ask == AudioTrial.Ask.START && attest.status == AttestState.NONE -> null // not now: no start, the trial kept
                        ask == AudioTrial.Ask.START -> api.start()
                        ask == AudioTrial.Ask.READ -> api.read()
                        else -> null
                    }
                }.getOrNull()
            }
            asking = false
            if (a != null) {
                val tier = if (AudioPlan.attested) AudioTrial.VOICES else AudioTrial.LIBRARY
                AudioPlan.change { before ->
                    val after = before.answered(a, System.currentTimeMillis())
                    // the tier is the start's: a trial begun now is as attested as the request that began it
                    if (ask == AudioTrial.Ask.START && after.status == AudioTrial.ACTIVE && before.status != AudioTrial.ACTIVE) after.copy(tier = tier) else after
                }
            }
        }
    }

    /** The voice service's verdict on this phone's key, for QA in a debug build (never the chain, nothing of the phone). */
    private fun verdict(r: Enrolled) {
        if (!BuildConfig.QA) return
        when (r) {
            is Enrolled.Yes -> Log.i(ATTEST_TAG, "verdict=enrolled level=${r.level} key=${r.state.keyId.take(8)}")
            is Enrolled.Refused -> Log.i(ATTEST_TAG, "verdict=refused reason=${r.reason}")
            is Enrolled.NotNow -> Log.i(ATTEST_TAG, "verdict=later reason=${r.reason}")
        }
    }

    /**
     * What QA asks in a debug build (app/QaHooks, "audio:trial:<what>"): "url:<url>" this build's voice service, tried at once
     * (blank: the build's own); "read" how it stands, read now whatever the day; "attest" a new key enrolled now, whatever its
     * state, and the voice service's verdict logged (tag GlasAttest: enrolled with its level, or refused with its reason code;
     * never the chain); "qakey" Jan's QA key taken from the file the QA script put in the app's folder (files/qa-glas-key,
     * through run-as: never on a command line), the file gone at once; "qakey:off" the key forgotten.
     */
    fun qa(what: String) {
        when {
            what.startsWith("url:") -> {
                AudioPlan.qaGlas(what.removePrefix("url:").trim())
                check(again = true)
            }
            what == "read" -> {
                AudioPlan.change { it.copy(readAt = 0) }
                check(again = true)
            }
            what == "attest" -> scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { verdict(AudioPlan.enroll(renew = AudioPlan.attest.value.status == AttestState.ENROLLED)) }
                        .onFailure { Log.i(ATTEST_TAG, "verdict=later reason=${it.javaClass.simpleName}") }
                }
            }
            what == "qakey" -> scope.launch {
                withContext(Dispatchers.IO) {
                    val f = java.io.File(files, QA_KEY_FILE)
                    val key = runCatching { f.readText() }.getOrNull()
                    f.delete()
                    AudioPlan.qaKey(key)
                    Log.i(ATTEST_TAG, if (key?.trim()?.startsWith("glas_") == true) "qakey=set" else "qakey=none")
                }
                voicesChanged()
            }
            what == "qakey:off" -> {
                AudioPlan.qaKey(null)
                voicesChanged()
            }
        }
    }

    private companion object {
        const val ATTEST_TAG = "GlasAttest"
        /** Where the QA script puts Jan's QA key for "audio:trial:qakey" (in the app's files, through run-as). */
        const val QA_KEY_FILE = "qa-glas-key"
        const val TAG = "AudioTrial"
        const val RETRY_MS = 30 * 60_000L
    }
}
