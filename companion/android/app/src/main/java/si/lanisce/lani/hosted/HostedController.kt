package si.lanisce.lani.hosted

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import si.lanisce.lani.data.BridgeConfig
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.phone.PhoneBridge
import si.lanisce.lani.phone.PhoneTransport
import si.lanisce.lani.sync.DataSync

/** What the hosted mode's onboarding asks, as lani-setup asks it (companion/bridge/src/cli/setup.ts). */
data class HostedAnswers(
    val name: String,
    /** "female", "male" or null (the tutor asks later). */
    val gender: String?,
    /** The language learned and the one explanations are in: sl, en, it, de. */
    val target: String,
    val base: String,
    val level: String = "A1",
    val goal: Int = 30,
    /** The learner is a child: the tutor speaks as to a child, keys and accounts are a parent's (app/ParentGate). */
    val child: Boolean = false,
) {
    fun json(): String = buildJsonObject {
        put("name", name.trim())
        gender?.let { put("gender", it) }
        put("target", target)
        put("base", base)
        put("level", level)
        put("goal", goal)
        if (child) put("child", true)
    }.toString()

    companion object {
        val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")
        val GOALS = listOf(10, 15, 20, 30, 45, 60)
        /** The languages the culture packs speak (companion/cultures: primorska, lakeland, friuli, kaernten). */
        val LANGUAGES = listOf("sl", "en", "it", "de")
    }
}

/**
 * "☁️ Lani na tem telefonu · Lani on this phone" for the screens (docs/plans/05-built/hosted-mode.md): the splash's
 * boot, the onboarding (a new learner's data, or an export imported, or the data already on the phone), the export and
 * the import from the settings, and the way back to a node. The bridge itself is [HostedRuntime]'s.
 *
 * The phone's bridge runs as the lease's phone (LANI_SIDE=phone, LANI_LEASE_DEVICE: [DataSync]'s device id), tells the
 * data's sync of its writes ([DataSync.touched]), and boots again when a pull brought new files ([DataSync.onPulled]).
 * With a relay set (Gradle's lani.relayUrl, or the setting here: the backup's relay too), its town and family page go
 * through it (LANI_RELAY_URL, LANI_TOWN_URL=relay); without one, towns aren't there in this mode.
 */
class HostedController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: Prefs,
    private val data: DataSync,
    private val appName: () -> String,
    private val deviceName: String,
) {
    /** This phone's connection is its own bridge. */
    var active by mutableStateOf(false)
        private set
    /** The splash: the phone's bridge booting as the app starts. */
    var booting by mutableStateOf(false)
        private set
    /** Why the phone's bridge didn't boot (the splash says it, with "Try again"). */
    var bootError by mutableStateOf<String?>(null)
        private set
    /** Something long under way, for the screens: "create", "import", "export". */
    var busy by mutableStateOf<String?>(null)
        private set
    /** The relay this phone's town would use; empty: none (BuildConfig's, or the one set here). */
    var relayUrl by mutableStateOf(data.relay)
        private set
    /** Towns and the family page need the relay in this mode; on a node they're the node's. */
    val townsAvailable: Boolean get() = !active || relayUrl.isNotBlank()

    /** Hooked up by the view model: a pull or an import put new data under the bridge (it booted again). */
    var onNewData: () -> Unit = {}

    private val childFile = File(si.lanisce.lani.phone.AppBridge.home(context), HostedRuntime.CHILD_FILE)

    /**
     * The learner on this phone is a child (the onboarding's "👧 Za otroka · For a child"): the bridge boots as a child's
     * profile (LANI_CHILD) and the app leaves keys and accounts to a parent (app/ParentGate).
     */
    var child by mutableStateOf(childFile.isFile)
        private set

    init {
        HostedRuntime.init(context)
        HostedRuntime.onWrite = { data.touched() }
        data.onPulled = { files ->
            // the repository brought files the phone's bridge reads: it boots again over them (its stores read some at boot)
            if (active && files.isNotEmpty()) scope.launch {
                HostedRuntime.say("a pull brought ${files.size} files: the bridge boots again")
                runCatching { HostedRuntime.restart() }
                onNewData()
            }
        }
    }

    /** Whether [c] is the hosted mode's connection (the phone's address). */
    fun isHosted(c: BridgeConfig?): Boolean = PhoneTransport.isPhone(c?.baseUrl)

    /** Whether this phone already holds a learner's data (an earlier hosted start, or git's pull), and whose. */
    fun learnerOnPhone(): String? = if (!HostedRuntime.hasLearner(context)) null
        else DataExport.learnerOf(File(HostedRuntime.dataDir(context), "learner-profile.json"))["name"]?.jsonPrimitive?.contentOrNull ?: "?"

    /**
     * The app starts with the hosted connection [c]: the phone's bridge boots (the splash shows meanwhile). True when it
     * is up; false with [bootError] set.
     */
    suspend fun boot(c: BridgeConfig): Boolean {
        data.keep() // this phone's id in the lease, kept (the bridge's LANI_LEASE_DEVICE reads it from the sync's settings)
        active = true
        booting = true
        bootError = null
        return try {
            HostedRuntime.ensure() ?: error("no hosted connection")
            true
        } catch (e: Exception) {
            HostedRuntime.say("the phone's bridge didn't boot: ${e.stackTraceToString()}")
            bootError = e.message ?: e.toString()
            false
        } finally {
            booting = false
        }
    }

    /**
     * A new learner (the onboarding's [answers]): their data from the templates, the bridge booted over it, the hosted
     * connection kept. The connection, or the problem's text.
     */
    suspend fun create(answers: HostedAnswers): Result<BridgeConfig> = work("create") {
        val token = PhoneBridge.randomToken()
        HostedRuntime.stop()
        // a fresh start over a learner's data on the phone: it is kept aside (the newest three), never overwritten
        if (HostedRuntime.hasLearner(context)) {
            val aside = withContext(Dispatchers.IO) { DataExport.setAside(HostedRuntime.dataDir(context), "before-fresh") }
            HostedRuntime.say("a fresh start: the data before is in $aside")
        }
        // before the bridge is made: its environment (LANI_CHILD) is read as it boots
        withContext(Dispatchers.IO) {
            childFile.parentFile?.mkdirs()
            if (answers.child) childFile.writeText("1\n") else childFile.delete()
        }
        child = answers.child
        val made = HostedRuntime.create(context, token)
        try {
            made.bridge.initLearner(answers.json())
        } catch (e: Exception) {
            withContext(Dispatchers.IO) { made.bridge.stop() }
            throw e
        }
        HostedRuntime.adopt(made)
        keep(token)
    }

    /** The data already on the phone (an earlier hosted start, or what git pulled): the bridge over it. */
    suspend fun goOn(): Result<BridgeConfig> = work("create") {
        val token = PhoneBridge.randomToken()
        HostedRuntime.stop()
        HostedRuntime.adopt(HostedRuntime.create(context, token))
        keep(token)
    }

    /**
     * An export ([uri]: `lani export` on a node, or this app's) in the data's place: unpacked, checked (its manifest;
     * validate-data and read-db through the db port), the phone's data moved aside first. During the onboarding
     * ([starting]) the connection is kept as the hosted mode's; from the settings the running bridge boots again over the
     * new data. What was imported, or the problem's text.
     */
    suspend fun import(uri: Uri, starting: Boolean): Result<Imported> = work("import") {
        val root = HostedRuntime.dataDir(context).parentFile!!
        val staging = File(root, "import")
        val token = if (starting) PhoneBridge.randomToken() else prefs.config()?.token ?: PhoneBridge.randomToken()
        // a child's export makes this phone's learner a child's (never the other way round): its marker set before a
        // bridge is made over the data (LANI_CHILD is read as it boots), taken back should the import fail
        val wasChild = childFile.isFile
        var imported = false
        try {
            val manifest = withContext(Dispatchers.IO) {
                (context.contentResolver.openInputStream(uri) ?: throw DataExport.Problem("the file can't be opened")).use { DataExport.unpack(it, staging) }
            }
            if (manifest.child && !wasChild) withContext(Dispatchers.IO) {
                childFile.parentFile?.mkdirs()
                childFile.writeText("1\n")
            }
            // the db port of the bridge that runs, or of one only loaded (nothing booted over the data yet)
            val running = HostedRuntime.bridge.takeIf { !starting }
            val loaded = if (running == null) HostedRuntime.create(context, token) else null
            val checker = running ?: loaded!!.bridge
            val problems = try {
                DataExport.checkData(staging) { checker.db(it) }
            } catch (e: Exception) {
                loaded?.bridge?.let { withContext(Dispatchers.IO) { it.stop() } }
                throw e
            }
            if (problems.isNotEmpty()) {
                loaded?.bridge?.let { withContext(Dispatchers.IO) { it.stop() } }
                throw DataExport.Problem(problems.take(3).joinToString("; "))
            }
            HostedRuntime.stop()
            val backup = withContext(Dispatchers.IO) { DataExport.replace(HostedRuntime.dataDir(context), staging) }
            HostedRuntime.say("imported ${manifest.files.size} files from ${manifest.fromSide} ${manifest.fromName} (${manifest.created}); the data before: $backup")
            if (loaded != null) {
                HostedRuntime.adopt(loaded)
            } else {
                HostedRuntime.ensure()
            }
            if (starting) keep(token)
            imported = true
            if (manifest.child) child = true
            Imported(manifest, backup)
        } finally {
            withContext(Dispatchers.IO) {
                if (!imported && !wasChild) childFile.delete()
                staging.deleteRecursively()
            }
        }
    }

    data class Imported(val manifest: DataExport.Manifest, val backup: File?)

    /** The phone's data as an export, in the cache's shared/ (the share sheet takes it from there: the FileProvider). */
    suspend fun export(): Result<DataExport.Written> = work("export") {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("lani-export-") }?.forEach { it.delete() }
        val data = HostedRuntime.dataDir(context)
        val now = Instant.now()
        val name = DataExport.learnerOf(File(data, "learner-profile.json"))["name"]?.jsonPrimitive?.contentOrNull
        val from = buildJsonObject {
            put("side", "phone")
            put("mode", "hosted")
            put("name", deviceName)
            put("app", appName())
        }
        withContext(Dispatchers.IO) { DataExport.write(data, File(dir, DataExport.fileName(name, now)), from, now, child = childFile.isFile) }
    }

    /**
     * Back to a node (the settings' "🖥️ To my computer"): the phone's bridge stopped and the hosted connection let go,
     * so setup pairs with a node. The phone's data stays where it is (`<filesDir>/lani/data`): coming back goes on with it.
     */
    suspend fun leave() {
        HostedRuntime.stop()
        prefs.forgetConnection()
        active = false
    }

    /** The relay's address for this phone (the backup's too); blank: none. The bridge boots again with it. */
    suspend fun setRelay(url: String) {
        data.setRelay(url)
        relayUrl = data.relay
        if (active) {
            HostedRuntime.restart()
            onNewData()
        }
    }

    private suspend fun keep(token: String): BridgeConfig {
        val c = HostedRuntime.config(token)
        prefs.saveConfig(c)
        prefs.resetEventCursors()
        active = true
        return c
    }

    private suspend fun <T> work(what: String, block: suspend () -> T): Result<T> {
        busy = what
        data.keep() // this phone's id in the lease, before the bridge boots as the lease's phone
        return try {
            Result.success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            HostedRuntime.say("$what failed: ${e.stackTraceToString()}")
            Result.failure(e)
        } finally {
            busy = null
        }
    }
}
