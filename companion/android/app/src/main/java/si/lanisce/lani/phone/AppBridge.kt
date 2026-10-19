package si.lanisce.lani.phone

import android.content.Context
import android.util.Log
import java.io.File

/**
 * The phone bridge over the app's own files (docs/plans/05-built/phone-runtime.md): everything under
 * `<filesDir>/lani` (the bridge's home), its learner data in `data/` (a node's data repository's layout: the six
 * databases, app/), the curated content in `project/` (unpacked from the APK, read only for the bridge), the compiled
 * bundle kept in `cache/`. Nothing of the bridge reads or writes outside `<filesDir>/lani`.
 */
object AppBridge {
    const val TAG = "LaniBridge"

    fun home(context: Context) = File(context.filesDir, "lani")
    fun projectDir(context: Context) = File(home(context), "project")
    fun dataDir(context: Context) = File(home(context), "data")

    /** The bundle in the APK (app/build.gradle.kts phoneBridgeBundle). */
    fun bundle(context: Context): String = context.assets.open("phone/bridge-phone.js").use { it.readBytes().decodeToString() }

    /** To the app's log, a long text in parts (logcat cuts a line at about 4 KB). */
    fun androidLog(level: String, text: String) {
        val priority = when (level) {
            "error" -> Log.ERROR
            "warn" -> Log.WARN
            "debug" -> Log.DEBUG
            else -> Log.INFO
        }
        for (part in text.chunked(3500)) Log.println(priority, TAG, part)
    }

    /**
     * The content unpacked when the app's version changed ([Unpacker]), then a bridge over [dataDir] (the app's own
     * unless given), not started yet: its first call starts it ([PhoneBridge.start]). Off the main thread: an unpack
     * copies about 12 MB (181 ms on the emulator).
     */
    fun create(
        context: Context,
        dataDir: File = dataDir(context),
        env: Map<String, String> = emptyMap(),
        log: (String, String) -> Unit = ::androidLog,
        /** The app's token for this bridge (the hosted mode keeps one: BridgeConfig.token); none: a random one. */
        token: String? = null,
        onDataWritten: (String) -> Unit = {},
    ): Pair<PhoneBridge, Unpacker.Result> {
        val home = home(context)
        val unpacked = Unpacker.of(context).unpack(projectDir(context))
        dataDir.mkdirs()
        val bridge = PhoneBridge(
            PhoneBridge.Config(
                projectDir = projectDir(context),
                dataDir = dataDir,
                home = home,
                bundle = { bundle(context) },
                env = env,
                log = log,
                bytecodeDir = File(home, "cache/bytecode"),
                token = token ?: PhoneBridge.randomToken(),
                onDataWritten = onDataWritten,
            ),
        )
        return bridge to unpacked
    }
}
