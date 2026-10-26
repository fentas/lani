package si.lanisce.lani.hosted

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.phone.AppBridge
import si.lanisce.lani.phone.PhoneBridge
import si.lanisce.lani.phone.PhoneTransport
import si.lanisce.lani.sync.DataSettings

/**
 * "☁️ Lani na tem telefonu · Lani on this phone" (docs/plans/05-built/hosted-mode.md): the phone's own bridge for the
 * whole process, the screens' and the background jobs' alike (the WorkManager checks run in the same process when it
 * lives). The hosted mode is the connection whose address is [PhoneTransport.BASE_URL]: its token is this bridge's
 * ([PhoneBridge.Config.token]), so every request of the app's client goes in through [transport] as it would to a node.
 *
 * - **Boot:** [ensure], off the main thread: the content unpacked when the app's version changed, the bundle loaded
 *   (from its bytecode after the first time), the bridge built and its features started (the rhythm's timers, the
 *   town's relay client). About half a second on the emulator; the app shows its splash meanwhile.
 * - **Stop:** [stop] ends it cleanly (its events kept, its streams closed): before an import takes the data's place,
 *   and when the learner switches to a node.
 * - **The data's sync:** the bridge runs as the lease's phone ([phoneEnvironment]); [onWrite] tells the git sync of each
 *   write (DataSync.touched: it pushes a moment later).
 */
object HostedRuntime {
    const val TAG = "LaniHosted"

    @Volatile private var app: Context? = null
    private val lock = Mutex()

    /** The bridge, once booted in this process. */
    @Volatile var bridge: PhoneBridge? = null
        private set

    /** How the last boot went: the content's unpack, the bundle, the bridge's own boot, from its making to up (ms). */
    data class BootInfo(val unpackMs: Double, val unpacked: Boolean, val loadMs: Double, val bootMs: Double, val fromBytecode: Boolean, val totalMs: Double)

    @Volatile var lastBoot: BootInfo? = null
        private set

    /** Told of every write to the learner's data (a mutating request the bridge took, the db port's writes). */
    @Volatile var onWrite: () -> Unit = {}

    /**
     * More environment for the bridge, read at each boot: [phoneEnvironment] (the lease's side and this phone's device,
     * the relay), also in a process a background job started without the screens.
     */
    @Volatile var environment: () -> Map<String, String> = { app?.let { phoneEnvironment(AppBridge.home(it)) }.orEmpty() }

    /**
     * The phone's bridge as the lease's phone (LANI_SIDE=phone, LANI_LEASE_DEVICE: the data sync's device id, from its
     * settings in [home]/sync, sync/DataSync) and, with a relay (the one set on the phone, else the build's: BuildConfig.RELAY_URL), its
     * town and family page through it (LANI_RELAY_URL, LANI_TOWN_URL=relay). Without a relay the towns aren't there.
     */
    fun phoneEnvironment(home: File, buildRelay: String = si.lanisce.lani.BuildConfig.RELAY_URL): Map<String, String> {
        val settings = runCatching {
            settingsJson.decodeFromString(DataSettings.serializer(), File(home, "sync/settings.json").readText())
        }.getOrNull()
        val relay = settings?.relay?.takeIf { it.isNotBlank() } ?: buildRelay
        return buildMap {
            put("LANI_SIDE", "phone")
            // the stories' daily check a while after the boot, not in it (features/stories.ts): Home comes first
            put("LANI_STORIES_CHECK_DELAY_MS", "20000")
            settings?.device?.takeIf { it.isNotBlank() }?.let { put("LANI_LEASE_DEVICE", it) }
            // a child's device (the onboarding's "for a child"): the bridge's profile is a child's, as a node's lani-profile --child
            if (File(home, CHILD_FILE).isFile) put("LANI_CHILD", "1")
            if (relay.isNotBlank()) {
                put("LANI_RELAY_URL", relay.trimEnd('/'))
                put("LANI_TOWN_URL", "relay")
            }
        }
    }

    private val settingsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** In the phone's home (`<filesDir>/lani`): there when the learner on this phone is a child (HostedController.child). */
    const val CHILD_FILE = "child"

    /** The app's client's way in (data/Http.kt installs it): requests to the phone's address answered by [bridge]. */
    val transport = PhoneTransport(bridge = { ensure() }, written = { _, _ -> onWrite() })

    /** Called by whatever runs first in the process (the view model, a worker): the context the bridge's files are in. */
    fun init(context: Context) {
        if (app == null) app = context.applicationContext
    }

    /** Whether the stored connection is the hosted mode's. */
    suspend fun active(context: Context): Boolean = PhoneTransport.isPhone(Prefs(context).config()?.baseUrl)

    /** The hosted mode's connection: the phone's address and its bridge's token. */
    fun config(token: String) = BridgeConfig(PhoneTransport.BASE_URL, token)

    /** The bridge, booted once per process; null when the hosted mode isn't on (or no context was given yet). */
    suspend fun ensure(): PhoneBridge? {
        bridge?.let { return it }
        val ctx = app ?: return null
        val config = Prefs(ctx).config()?.takeIf { PhoneTransport.isPhone(it.baseUrl) } ?: return null
        return lock.withLock { bridge ?: boot(ctx, config.token) }
    }

    /** A bridge made by [create], not booted yet: when its making began, the content's unpack (whether it unpacked). */
    class Made(val bridge: PhoneBridge, val unpackMs: Double, val unpacked: Boolean, internal val t0: Long)

    /**
     * A bridge over [ctx]'s data for [token], not booted: the onboarding makes the learner's data with it first
     * ([PhoneBridge.initLearner]), an import checks an export with its db port. [adopt] makes it the process's.
     */
    suspend fun create(ctx: Context, token: String): Made = withContext(Dispatchers.IO) {
        init(ctx)
        val t0 = System.nanoTime()
        val (b, unpacked) = AppBridge.create(ctx, env = environment(), token = token, onDataWritten = { onWrite() })
        if (unpacked.unpacked) say("content unpacked: ${unpacked.files} files, ${unpacked.bytes / 1024} KB, ${"%.0f".format(unpacked.millis)} ms")
        Made(b, unpacked.millis, unpacked.unpacked, t0)
    }

    /** [m] booted and kept as the process's bridge. */
    suspend fun adopt(m: Made): PhoneBridge = lock.withLock {
        bridge?.takeIf { it !== m.bridge }?.let { old -> withContext(Dispatchers.IO) { old.stop() } }
        started(m)
    }

    private suspend fun boot(ctx: Context, token: String): PhoneBridge = started(create(ctx, token))

    private suspend fun started(m: Made): PhoneBridge = withContext(Dispatchers.IO) {
        val b = m.bridge
        val boot = try {
            b.start()
        } catch (e: Exception) {
            b.stop()
            throw e
        }
        lastBoot = BootInfo(m.unpackMs, m.unpacked, boot.loadMs, boot.bootMs, boot.fromBytecode, (System.nanoTime() - m.t0) / 1e6)
        say("the phone's bridge is up: ${lastBoot}")
        bridge = b
        b
    }

    /** The bridge ends cleanly (its events kept, its streams closed); the next request boots it again. */
    suspend fun stop() = lock.withLock {
        val b = bridge ?: return@withLock
        bridge = null
        withContext(Dispatchers.IO) { b.stop() }
        say("the phone's bridge stopped")
    }

    /** Stopped and booted again (new data under it: an import, a pull of the data repository). */
    suspend fun restart(): PhoneBridge? {
        stop()
        return ensure()
    }

    /** The learner's data on this phone: `<filesDir>/lani/data` (a node's data repository's layout). */
    fun dataDir(context: Context): File = AppBridge.dataDir(context)

    /** To the app's log (tag LaniHosted); nothing in a JVM test, which has no Android log. */
    fun say(text: String) {
        runCatching { Log.i(TAG, text) }
    }

    /** Whether this phone holds a learner's data (a profile in the data directory). */
    fun hasLearner(context: Context): Boolean = File(dataDir(context), "learner-profile.json").isFile
}
