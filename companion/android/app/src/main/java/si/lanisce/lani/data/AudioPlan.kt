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
    private val attestNow = MutableStateFlow(AttestState())
    private var store: AudioTrialStore? = null

    /** The trial as this process knows it; every change is kept on the device at once ([change]). */
    val trial: StateFlow<AudioTrial> get() = now

    /** The attested key as this process knows it (data/GlasAttest.kt); kept on the device at once ([changeAttest]). */
    val attest: StateFlow<AttestState> get() = attestNow

    /** Where attested keys are made and kept: AndroidKeyStore (a test has its own). */
    var keys: AttestKeys = AndroidAttestKeys

    /** Reads the device's trial, once a process. */
    @Synchronized
    fun open(context: Context) {
        if (store != null) return
        store = AudioTrialStore(context).also {
            now.value = it.read()
            attestNow.value = it.attest()
        }
        GlasAuth.onRenew = { changeAttest { a -> if (a.status == AttestState.ENROLLED) a.copy(renew = true) else a } }
        sign()
    }

    /** Changes the attested key's state and keeps it, in one step; the requests are signed by the key that counts now. */
    @Synchronized
    fun changeAttest(how: (AttestState) -> AttestState) {
        val next = how(attestNow.value)
        if (next == attestNow.value) return
        attestNow.value = next
        store?.writeAttest(next)
        sign()
    }

    /** What [GlasAuth] signs with: the enrolled key while it counts, for this voice service. */
    private fun sign() {
        val a = attestNow.value
        GlasAuth.glas = glas
        GlasAuth.key = if (glas.isNotBlank() && a.attested(System.currentTimeMillis())) runCatching { keys.key(a.alias) }.getOrNull()?.takeIf { it.keyId == a.keyId } else null
    }

    /** Whether the requests are signed by an attested key now. */
    val attested: Boolean get() = GlasAuth.key != null

    /**
     * Enrolls a new key with the voice service, or renews ([renew]) the enrolled one: the outcome kept, the requests signed by
     * it from now on. Off the main thread: the keystore and the network.
     */
    fun enroll(renew: Boolean): Enrolled {
        val before = attestNow.value
        val r = GlasEnroll(glas, keys, pending = { alias, keyId -> changeAttest { it.copy(pendingAlias = alias, pendingKeyId = keyId) } }).enroll(before, renew)
        val at = System.currentTimeMillis()
        changeAttest { a ->
            when (r) {
                is Enrolled.Yes -> r.state
                is Enrolled.Refused -> {
                    // a key the old one can't vouch for any more: forget it, the next enrollment is a new install's
                    if (a.alias.isNotEmpty()) keys.delete(a.alias)
                    a.refused(r.reason, at, if (r.reason == GlasEnroll.NOT_CONFIGURED) AudioTrial.DAY_MS else AttestState.REFUSED_RETRY_MS)
                }
                is Enrolled.NotNow -> a.failedForNow(r.reason, at)
            }
        }
        return r
    }

    /** QA's hook "audio:trial:qakey" (a debug build): Jan's QA key, a key with `app`; null clears it. Never in a log. */
    fun qaKey(key: String?) {
        store?.qaKey = key?.trim()?.takeIf { it.startsWith("glas_") }
    }

    /** The QA key in force: a debug build's only. */
    private val qaKey: String? get() = if (BuildConfig.QA) store?.qaKey else null

    /** The voice service (Gradle's lani.glasUrl), or QA's in a debug build; empty: no trial, the app as it was. */
    val glas: String get() = ((if (BuildConfig.QA) store?.qaGlas else null) ?: BuildConfig.GLAS_URL).trimEnd('/')

    /** QA's hook "audio:trial:url:<url>" (a debug build): the voice service of a build with none; blank: the build's. */
    fun qaGlas(url: String) {
        store?.qaGlas = url.ifBlank { null }
        sign()
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
        voices(connection, now.value, glas, L10n.ownPair.target.code, System.currentTimeMillis(), qaKey)

    override fun keepsHeard(connection: BridgeConfig): Boolean =
        keepsHeard(connection, now.value, glas, L10n.ownPair.target.code, System.currentTimeMillis())

    override fun ended() = change { it.ended(System.currentTimeMillis()) }

    /**
     * [voices] with all it depends on said: a node's connection stays the node's, whatever the trial's state ([trial]). [qaKey]:
     * a debug build's QA key (a key with `app`, attested in the voice service's tiers): the voice service's routes with it,
     * trial or not; a release build never has one ([AudioPlan.qaKey]).
     */
    fun voices(connection: BridgeConfig, trial: AudioTrial, glas: String, lang: String, now: Long, qaKey: String? = null): BridgeConfig {
        if (!PhoneTransport.isPhone(connection.baseUrl)) return connection
        if (qaKey != null && glas.isNotBlank()) return BridgeConfig("${glas.trimEnd('/')}/v1/app/$lang", qaKey)
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
