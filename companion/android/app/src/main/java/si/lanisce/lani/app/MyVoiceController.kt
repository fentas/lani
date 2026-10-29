package si.lanisce.lani.app

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.audio.ClipFiles
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.MineVoice
import si.lanisce.lani.data.MyVoiceApi
import si.lanisce.lani.data.MyVoiceOption
import si.lanisce.lani.data.MyVoiceState
import si.lanisce.lani.data.MyVoices
import si.lanisce.lani.data.Speaker
import java.io.File

/**
 * "🗣️ Moj glas · My voice" (companion/SCENES.md, "The learner's voice"; docs/plans/05-built/my-voice.md): the voice the
 * learner's own lines are said in, chosen from 2–3 voices of their gender and age (a child's for a child) on the node, or
 * off. In the settings' "🔊 Zvok in glas · Sound and voice", and offered once at the first line of theirs a dialog says
 * ([offerAtLine]: a sheet over the dialog, which goes on; a tap picks a voice, "✓ V redu · OK" or a tap beside it keeps the
 * default, and the node remembers either). Each voice's ▶ says its sample ("Ciao! Sono Teo.") in it: made on the node the
 * first time, kept after. Compose state, on the main thread.
 */
class MyVoiceController(
    private val scope: CoroutineScope,
    private val clips: Clips,
    private val speaker: Speaker,
) {
    private var api: MyVoiceApi? = null

    /** What the node said last of the learner's voice (GET or POST /learner/voice); null before, or from an older node. */
    var state by mutableStateOf<MyVoiceState?>(null)
        private set

    /** The node's answer is on its way. */
    var loading by mutableStateOf(false)
        private set

    /** The choice on its way to the node: the option's id, "off" or "default". */
    var saving by mutableStateOf<String?>(null)
        private set

    /** The option whose sample is being said now. */
    var playing by mutableStateOf<String?>(null)
        private set

    /** What went wrong last, for the page to say (the node unreachable, an older node). */
    var problem by mutableStateOf<Problem?>(null)
        private set

    /** The offer at the first line is showing. */
    var offering by mutableStateOf(false)
        private set

    /** The offer was shown while the app runs (once: the node remembers the choice for good). */
    private var offered = false

    private var sampleJob: Job? = null

    enum class Problem { OFFLINE, OLD_NODE, NOT_KEPT }

    /** The learner's voice as the phone plays their lines with (the node's GET /voice/profiles' "mine"). */
    val mine: MineVoice? get() = clips.mine

    fun connect(config: BridgeConfig) {
        api = MyVoiceApi(config)
    }

    /** Asks the node what the learner may choose from, and what they hear now. */
    fun load() {
        val a = api ?: return Unit.also { problem = Problem.OFFLINE }
        loading = true
        scope.launch {
            val s = try {
                withContext(Dispatchers.IO) { a.state() }.also { if (it == null) problem = Problem.OLD_NODE else problem = null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "the learner's voice: ${e.message}")
                problem = Problem.OFFLINE
                null
            }
            loading = false
            if (s != null) state = s
        }
    }

    /**
     * The learner's line is about to be said in their voice ([Speaker.onOwnLine]): the choice is offered now when it is
     * due ([MyVoices.offerDue]: they haven't chosen, their lines are on, the node has voices for them; once a run), at
     * home only (on a visit the town's voice of their kind says them, in the town's language).
     */
    fun offerAtLine() {
        if (si.lanisce.lani.data.VisitWorld.active || !MyVoices.offerDue(mine, offered)) return
        offered = true
        if (BuildConfig.QA) Log.i(TAG, "offered at the first line")
        load()
        offering = true
    }

    /**
     * The offer closed: [picked] a voice (kept by [choose]), else the default kept ([MyVoices.DEFAULT]: a tap on "✓ V redu ·
     * OK" or beside the sheet), so it isn't offered again on any of the learner's devices.
     */
    fun closeOffer(picked: Boolean = false) {
        offering = false
        stopSample()
        if (!picked && mine?.chosen != true) choose(MyVoices.DEFAULT)
    }

    /** Keeps [choice] (an option's id, "off", "default") on the node; their lines are in it from the next one on. */
    fun choose(choice: String) {
        val a = api ?: return Unit.also { problem = Problem.OFFLINE }
        saving = choice
        scope.launch {
            val s = try {
                withContext(Dispatchers.IO) { a.choose(choice) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "the learner's voice not kept: ${e.message}")
                problem = Problem.NOT_KEPT
                null
            }
            saving = null
            if (s != null) {
                problem = null
                state = s
                clips.mineSaid(MyVoices.mineOf(s))
                if (BuildConfig.QA) Log.i(TAG, "chosen $choice: ${s.voice} (off ${s.off})")
            }
        }
    }

    /**
     * [o]'s sample ([MyVoiceState.sample]) in it: its clip on the phone, else from the node (made there the first time),
     * else the phone's own voice as it would say the learner's line in it. A second tap on the one playing stops it.
     */
    fun sample(o: MyVoiceOption) {
        if (playing == o.id) return stopSample()
        val s = state ?: return
        val a = api
        sampleJob?.cancel()
        playing = o.id
        val like = MineVoice(voice = o.voice, id = o.id, name = o.name, gender = o.gender, age = o.age, off = false, chosen = true)
        sampleJob = scope.launch {
            try {
                speaker.sample(s.sample, like) { fetch(a, s.sample, o) }
            } finally {
                if (playing == o.id) playing = null
            }
        }
    }

    fun stopSample() {
        if (playing == null) return
        playing = null
        sampleJob?.cancel()
        speaker.stop()
    }

    /** [o]'s sample clip on the phone: there already, else got from the node (null when it can't make it now). */
    private suspend fun fetch(a: MyVoiceApi?, text: String, o: MyVoiceOption): File? {
        clips.onDevice(text, listOf(o.voice))?.firstOrNull()?.let { return it }
        a ?: return null
        return withContext(Dispatchers.IO) {
            val url = a.sample(o.id).second ?: return@withContext null
            val f = clips.file(url)
            if (!ClipFiles.get(f, emptyList()) { to -> a.download(url, to) }) return@withContext null
            f.setLastModified(System.currentTimeMillis())
            f
        }
    }

    /** QA (a debug build, app/QaHooks "myvoice:offer"): the offer may show again at the next line, as if never chosen. */
    fun qaOfferAgain() {
        offered = false
        clips.mine?.let { clips.mineSaid(it.copy(chosen = false)) }
    }

    companion object {
        private const val TAG = "MyVoice"
    }
}
