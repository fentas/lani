package si.lanisce.lani.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.phone.PhoneTransport

/** Who answers a connection's voice routes, as [ClipsApi] and [Clips] ask it: [AudioPlan]; a test has its own. */
interface VoicePlan {
    /** Who answers index, say, prepare and a clip's file for [connection] right now. */
    fun voices(connection: BridgeConfig): BridgeConfig

    /** [connection] itself answers, and it can't know the clips the phone got elsewhere: they stay ([PreviousClips.heard]). */
    fun keepsHeard(connection: BridgeConfig): Boolean

    /** A voice route said the trial is over. */
    fun ended()
}

/**
 * The audio service's one place in the app (docs/plans/05-built/first-minutes.md, "What this leaves for the audio trial"): who
 * makes the villagers' clips. The connection the learner has: the family's computer, or on this device its own bridge, whose
 * /voice/say answers 204 so that the phone's voice speaks. For a learner on this device the trial answers the same routes
 * while it lasts ([voices]; [addOn] would, a paid plan, once there is one). Asked at every request, not at connect, so an end
 * in the middle of a session takes effect at once.
 *
 * Only the routes that make or fetch clips come here: index, say, prepare and the clip's file ([ClipsApi]). /voice/profiles,
 * /voice/redo, /voice/offline*, /voice/sample and a visit's routes stay on the connection.
 *
 * When it ends the connection answers again, the clips already on the device stay ([keepsHeard]) and the rest falls back
 * through [Route] to Piper or the phone's voice: nothing is lost and nothing else in the app changes.
 *
 * A process reads the trial with [open] before it asks anything: the app, the day's prefetch, the car's library.
 */
object AudioPlan : VoicePlan {
    private val now = MutableStateFlow(AudioTrial())
    private var store: AudioTrialStore? = null

    /** The trial as this process knows it; every change is kept on the device at once ([change]). */
    val trial: StateFlow<AudioTrial> get() = now

    /** Reads the device's trial, once a process. */
    @Synchronized
    fun open(context: Context) {
        if (store != null) return
        store = AudioTrialStore(context).also { now.value = it.read() }
    }

    /** The voice service (Gradle's lani.glasUrl), or QA's in a debug build; empty: no trial, the app as it was. */
    val glas: String get() = ((if (BuildConfig.QA) store?.qaGlas else null) ?: BuildConfig.GLAS_URL).trimEnd('/')

    /** QA's hook "audio:trial:url:<url>" (a debug build): the voice service of a build with none; blank: the build's. */
    fun qaGlas(url: String) {
        store?.qaGlas = url.ifBlank { null }
    }

    /** Changes the trial and keeps it, in one step: the app, the prefetch and a 403 may all do it at once. */
    @Synchronized
    fun change(how: (AudioTrial) -> AudioTrial) {
        val next = how(now.value)
        if (next == now.value) return
        now.value = next
        store?.write(next)
    }

    override fun voices(connection: BridgeConfig): BridgeConfig =
        voices(connection, now.value, glas, L10n.ownPair.target.code, System.currentTimeMillis())

    override fun keepsHeard(connection: BridgeConfig): Boolean =
        keepsHeard(connection, now.value, glas, L10n.ownPair.target.code, System.currentTimeMillis())

    override fun ended() = change { it.ended(System.currentTimeMillis()) }

    /** [voices] with all it depends on said: a node's connection stays the node's, whatever the trial's state ([trial]). */
    fun voices(connection: BridgeConfig, trial: AudioTrial, glas: String, lang: String, now: Long): BridgeConfig {
        if (!PhoneTransport.isPhone(connection.baseUrl)) return connection
        return addOn(connection) ?: trial.voices(glas, lang, now) ?: connection
    }

    /**
     * [keepsHeard] with all it depends on said: on this device's own bridge, once a trial began here. On a device that never
     * had one nothing changes, and a node's index takes over as it always did.
     */
    fun keepsHeard(connection: BridgeConfig, trial: AudioTrial, glas: String, lang: String, now: Long): Boolean =
        PhoneTransport.isPhone(connection.baseUrl) && trial.startedAt > 0 && voices(connection, trial, glas, lang, now) == connection

    /**
     * THE PLACE FOR A PAID PLAN (plan 3, the audio add-on: the villagers' voices and live lines, priced near what audio costs).
     * A learner with one would get its own [BridgeConfig] here, the same routes as the trial's with the plan's key, and it
     * comes before the trial. Nothing sells one and nothing charges anyone, so today this gives nothing.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun addOn(connection: BridgeConfig): BridgeConfig? = null
}
