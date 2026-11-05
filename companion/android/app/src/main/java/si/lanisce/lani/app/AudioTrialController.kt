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
import si.lanisce.lani.data.AudioTrial
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
     * Starts the trial or reads how it stands when that's due ([AudioTrial.ask]). The learner came to this device, the app came
     * on screen; [again]: the network came back, so a failed ask needn't wait.
     */
    fun check(again: Boolean = false) {
        if (!offered) return
        val now = System.currentTimeMillis()
        AudioPlan.change { it.settled(now).withId() }
        val t = AudioPlan.trial.value
        val ask = t.ask(now) ?: return
        if (asking || (!again && now - askedAt < RETRY_MS)) return
        asking = true
        askedAt = now
        val api = TrialApi(glas(), t.id)
        scope.launch {
            val a: TrialAnswer? = withContext(Dispatchers.IO) {
                runCatching { if (ask == AudioTrial.Ask.START) api.start() else api.read() }.getOrNull()
            }
            asking = false
            if (a != null) AudioPlan.change { it.answered(a, System.currentTimeMillis()) }
        }
    }

    /**
     * What QA asks in a debug build (app/QaHooks, "audio:trial:<what>"): "url:<url>" this build's voice service, tried at once
     * (blank: the build's own); "read" how it stands, read now whatever the day.
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
        }
    }

    private companion object {
        const val TAG = "AudioTrial"
        const val RETRY_MS = 30 * 60_000L
    }
}
