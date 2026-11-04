package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import si.lanisce.lani.data.Prefs

/**
 * What the first run left for later (docs/plans/05-built/first-minutes.md; Jan: "easy, comfortable, not overloading"): a new
 * learner meets the village first, on the defaults ([MINUTES], level A1, aim B2, no gender said, the node's voice), and each
 * of the rest is asked when it matters, once. A card dismissed stays dismissed: [settle] for good, whatever was answered.
 * Pure; kept on the device by [FirstRunController].
 *
 * Nothing here ever applies to an existing learner: [fresh] is set only by a first run ([begin]), so Jan's and Teo's
 * devices, which have a village and never found one, have it false for good, with no migration. The school's card
 * ([Ask.PLACEMENT]) is the one that any learner who hasn't begun may dismiss.
 */
data class FirstRun(
    /** This device's learner came through the first run: the moment and the later questions are theirs. */
    val fresh: Boolean = false,
    /** On this device alone: nobody asked the aim and the minutes elsewhere, and the tutor is theirs to set up. */
    val onDevice: Boolean = false,
    /** What was asked or dismissed. */
    val settled: Set<Ask> = emptySet(),
) {
    /**
     * [MOMENT] the line said and the small win after the founding; [PACE] the aim and the minutes after the first session;
     * [VOICE] "Which voice is yours?" after the first dialog; [TUTOR] the ways to a tutor when it's first opened; [PLACEMENT]
     * the school's "Know some already?".
     */
    enum class Ask(val id: String) { MOMENT("moment"), PACE("pace"), VOICE("voice"), TUTOR("tutor"), PLACEMENT("placement") }

    /** A first run began here. Once: a second call changes nothing. */
    fun begin(onDevice: Boolean): FirstRun = if (fresh) this else copy(fresh = true, onDevice = onDevice)

    /** [a] was asked, answered or dismissed: never again. */
    fun settle(a: Ask): FirstRun = copy(settled = settled + a)

    /** The village's first line and the small win come next (the founding comes before them). */
    val moment: Boolean get() = fresh && Ask.MOMENT !in settled

    /** The aim and the minutes, after a finished session ([practised]); a child's are a parent's, in the settings. */
    fun pace(practised: Boolean, child: Boolean): Boolean = fresh && onDevice && !child && practised && Ask.PACE !in settled

    /** "Which voice is yours?" waits for the end of the first dialog instead of coming over its first line. */
    val voiceAfterDialog: Boolean get() = fresh && Ask.VOICE !in settled

    /** The ways to a tutor, the first time the tutor is opened on this device with none set up ([ready]). */
    fun tutor(ready: Boolean): Boolean = fresh && onDevice && !ready && Ask.TUTOR !in settled

    /** The school's card may show: anyone's, until dismissed. */
    val placementCard: Boolean get() = Ask.PLACEMENT !in settled

    /**
     * The system's question about notifications waits: no dialog over the founding or the first win, nor over the question
     * after the first session; after that it comes as it does for everyone, once.
     */
    fun holdsNotifications(practised: Boolean, child: Boolean): Boolean = fresh && (!practised || pace(practised, child))

    /** For the device's preferences: "fresh,device|moment,pace". */
    fun encode(): String = listOfNotNull("fresh".takeIf { fresh }, "device".takeIf { onDevice }).joinToString(",") + "|" + settled.joinToString(",") { it.id }

    companion object {
        /** The minutes a day a first run starts on: easy to keep; the question after the first session changes it. */
        const val MINUTES = 15

        /** What [encode] wrote; a device with nothing kept (every existing learner's) is not [fresh]. */
        fun decode(s: String?): FirstRun {
            val parts = s.orEmpty().split("|")
            val flags = parts.first().split(",")
            val settled = parts.getOrNull(1).orEmpty().split(",").mapNotNull { id -> Ask.entries.firstOrNull { it.id == id } }.toSet()
            return FirstRun(fresh = "fresh" in flags, onDevice = "device" in flags, settled = settled)
        }
    }
}

/** [FirstRun] for the screens, kept in the device's preferences as it changes. Compose state, on the main thread. */
class FirstRunController(private val scope: CoroutineScope, private val prefs: Prefs) {
    var state by mutableStateOf(FirstRun())
        private set

    /** Read from the preferences: until then nothing is known of the learner (the notification question waits for it). */
    var loaded by mutableStateOf(false)
        private set

    init {
        scope.launch {
            state = FirstRun.decode(runCatching { prefs.firstRun() }.getOrNull())
            loaded = true
        }
    }

    fun begin(onDevice: Boolean) = keep(state.begin(onDevice))

    fun settle(a: FirstRun.Ask) = keep(state.settle(a))

    private fun keep(f: FirstRun) {
        if (f == state) return
        state = f
        scope.launch { runCatching { prefs.saveFirstRun(f.encode()) } }
    }
}
