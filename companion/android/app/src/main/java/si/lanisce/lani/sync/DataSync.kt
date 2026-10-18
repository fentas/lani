package si.lanisce.lani.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** The data's sync and backup settings (<filesDir>/lani/sync/settings.json). No secret: those are in the [SecretBox]. */
@Serializable
data class DataSettings(
    /** This phone's id in the lease: "p_" and 8 hex digits, made once. */
    val device: String = "",
    /** The data repository on GitHub, "owner/name", and its branch. */
    val repo: String? = null,
    val branch: String = "main",
    val repoPrivate: Boolean = true,
    /** Who the token belongs to (GitHub's login), and how it came: "device" (signed in) or "token" (pasted). */
    val login: String? = null,
    val via: String? = null,
    /** The backup: "code" (a recovery code, shown once) or "passphrase"; null until it is set up. */
    val backup: String? = null,
    val lastBackup: String? = null,
    val lastBackupName: String? = null,
    /** The relay's address, when the developer set one (else BuildConfig.RELAY_URL). */
    val relay: String? = null,
)

/**
 * The debounced push: a write [touched] it; it pushes once the writes are [quietMs] apart, and at the latest [maxWaitMs]
 * after the first; [flush] pushes now (the app goes to the background).
 */
class PushScheduler(
    private val scope: CoroutineScope,
    private val quietMs: Long,
    private val maxWaitMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
    private val push: suspend () -> Unit,
) {
    private var job: Job? = null
    private var first: Long? = null

    val pending: Boolean @Synchronized get() = first != null

    @Synchronized
    fun touched() {
        val t = now()
        val since = first ?: t.also { first = it }
        val wait = minOf(quietMs, maxOf(0L, since + maxWaitMs - t))
        job?.cancel()
        job = scope.launch {
            delay(wait)
            fire()
        }
    }

    @Synchronized
    fun flush() {
        if (first == null) return
        job?.cancel()
        job = scope.launch { fire() }
    }

    private suspend fun fire() {
        synchronized(this) {
            first = null
            job = null
        }
        push()
    }
}

/**
 * "💾 Podatki · Data" (ui/data/DataSheet.kt, docs/plans/05-built/git-sync.md): the phone's data directory
 * (<filesDir>/lani/data) synced with a repository on GitHub, the lease and the handover, and the encrypted backup. All
 * of it is opt-in: until git is connected nothing runs, and a phone in the self-hosted mode stays exactly as it was.
 *
 * - **Git:** signed in with the device flow ([DeviceFlow]) or a pasted fine-grained token; a repository picked or made.
 *   The sync pulls when the app comes to the front, pushes a moment after writes ([touched], debounced) and when it goes
 *   to the background, while the phone holds the lease. The hosted mode's bridge calls [touched] after it writes, reads
 *   [mayWrite], and hears [onPulled].
 * - **The lease:** [take] (handed over by `lani handover`, or a confirmed takeover), [handOver] back to the node.
 * - **The handover's QR code:** [receive] keeps the town's secrets (Handover.keep) and offers the repository it names.
 * - **The backup:** a recovery code or a passphrase ([setUpCode], [setUpPassphrase]); [backupNow], daily by itself;
 *   [restore], also on a new phone from the code alone.
 */
class DataSync(
    private val root: File,
    private val scope: CoroutineScope,
    private val box: SecretBox,
    private val deviceName: String,
    private val clientId: String,
    private val relayUrl: String,
    private val appVersion: String = "",
    private val api: String = "https://api.github.com",
    private val web: String = "https://github.com",
    private val http: OkHttpClient = GitHub.DEFAULT_CLIENT,
    private val clock: () -> Instant = Instant::now,
    private val log: (String) -> Unit = {},
    quietMs: Long = 20_000,
    maxWaitMs: Long = 120_000,
) {
    val dataDir = File(root, "data")
    private val syncDir = File(root, "sync")
    private val settingsFile = File(syncDir, "settings.json")
    private val stateFile = File(syncDir, "state.json")

    var settings by mutableStateOf(loadSettings())
        private set
    /** The last sync's outcome, and when. */
    var last by mutableStateOf<SyncOutcome?>(null)
        private set
    var lastAt by mutableStateOf<Instant?>(runCatching { syncState()?.lastSync?.let(Instant::parse) }.getOrNull())
        private set
    var lease by mutableStateOf<LeaseRead>(readLease())
        private set
    var busy by mutableStateOf(false)
        private set

    /** Told the files a pull changed (the hosted mode's bridge reads them again). */
    var onPulled: (List<String>) -> Unit = {}

    /** This phone in the lease. */
    val me: Party get() = Party(Party.PHONE, settings.device, deviceName.take(60))
    val connected: Boolean get() = settings.repo != null
    val signInAvailable: Boolean get() = clientId.isNotBlank()
    val relay: String get() = settings.relay?.takeIf { it.isNotBlank() } ?: relayUrl
    val leaseState: LeaseState get() = lease.stateFor(me, clock(), hasData = hasData())

    /** Whether the phone may write the learner's data now (its bridge asks): it holds the lease, or git isn't on at all. */
    val mayWrite: Boolean get() = !connected || leaseState == LeaseState.MINE

    private val scheduler = PushScheduler(scope, quietMs, maxWaitMs) { runSync() }
    private var ticker: Job? = null

    // --- settings ----------------------------------------------------------------------------------

    /** The settings; a phone without any gets its id here, saved once it is shown or used ([keep]): nothing is written before. */
    private fun loadSettings(): DataSettings {
        val s = runCatching { syncRead.decodeFromString(DataSettings.serializer(), settingsFile.readText()) }.getOrNull() ?: DataSettings()
        if (s.device.isNotBlank()) return s
        return s.copy(device = "p_" + ByteArray(4).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) })
    }

    /** Saves the settings (this phone's id with them) when they aren't yet: the Data sheet shows the id, so it stays. */
    fun keep() {
        if (!settingsFile.isFile) saveSettings(settings)
    }

    private fun saveSettings(s: DataSettings) {
        syncDir.mkdirs()
        val tmp = File(settingsFile.path + ".tmp")
        tmp.writeText(syncJson.encodeToString(DataSettings.serializer(), s))
        tmp.renameTo(settingsFile)
    }

    private fun update(f: (DataSettings) -> DataSettings) {
        settings = f(settings).also(::saveSettings)
    }

    private fun syncState(): SyncState? = runCatching { syncRead.decodeFromString(SyncState.serializer(), stateFile.readText()) }.getOrNull()

    private fun readLease(): LeaseRead = Lease.parse(File(dataDir, Lease.FILE).takeIf { it.isFile }?.readText())

    private fun hasData(): Boolean = dataDir.listFiles()?.any { it.name != "app" && it.name != ".gitignore" } == true ||
        File(dataDir, "app").listFiles()?.any { it.name != "lease.json" } == true

    /** Whether a divergence waits for the learner (the files both sides changed). */
    val diverged: Divergence? get() = syncState()?.diverged

    // --- GitHub ------------------------------------------------------------------------------------

    private fun github() = GitHub({ box.get(SecretBox.GIT_TOKEN) }, api, http, clock)

    fun deviceFlow() = DeviceFlow(clientId, web, http, clock)

    /** Keeps [token] after checking it with GitHub; the error's message, or null. */
    suspend fun useToken(token: String, via: String): String? {
        val t = token.trim()
        if (t.length < 20 || t.any { it.isWhitespace() }) return "not a GitHub token"
        val login = try {
            GitHub({ t }, api, http, clock).user()
        } catch (e: IOException) {
            return e.message ?: "GitHub refused the token"
        }
        box.put(SecretBox.GIT_TOKEN, t)
        update { it.copy(login = login, via = via) }
        return null
    }

    suspend fun signedIn(): Boolean = box.get(SecretBox.GIT_TOKEN) != null

    suspend fun repos(): List<GitHubRepo> = github().repos()

    suspend fun createRepo(name: String): GitHubRepo = github().createRepo(name, "Lani: the learner's data (private)")

    /** Syncs with [repo] from now on: a new repository starts a new sync (the phone's data stays as it is). */
    fun choose(repo: GitHubRepo) {
        update { it.copy(repo = repo.full, branch = repo.defaultBranch.ifBlank { "main" }, repoPrivate = repo.private) }
        stateFile.delete()
        lastAt = null
        last = null
        syncNow()
    }

    /** Git off: the token goes, the repository is forgotten; the phone's data stays. */
    suspend fun disconnect() {
        box.put(SecretBox.GIT_TOKEN, null)
        update { it.copy(repo = null, login = null, via = null) }
        stateFile.delete()
        last = null
        lastAt = null
    }

    private fun engine(): SyncEngine? {
        val ref = settings.repo?.let { RepoRef.parse(it, settings.branch) } ?: return null
        return SyncEngine(dataDir, stateFile, github(), ref, me, clock, log, onPulled = { files ->
            lease = readLease()
            onPulled(files)
        })
    }

    // --- the sync ----------------------------------------------------------------------------------

    /** A write of the phone's: pushed a moment later (debounced). */
    fun touched() {
        if (connected) scheduler.touched()
    }

    fun syncNow(): Job = scope.launch { runSync() }

    private suspend fun run(what: suspend (SyncEngine) -> SyncOutcome): SyncOutcome {
        val e = engine() ?: return SyncOutcome.Failed("git isn't connected")
        busy = true
        return try {
            withContext(Dispatchers.Default) { what(e) }.also {
                last = it
                lastAt = clock()
                lease = readLease()
                if (it is SyncOutcome.Failed) log("sync: ${it.message}")
            }
        } finally {
            busy = false
        }
    }

    private suspend fun runSync(): SyncOutcome = run { it.sync() }

    suspend fun take(force: Boolean = false): SyncOutcome = run { it.take(force) }

    suspend fun handOver(): SyncOutcome = run { it.handOver(Party(Party.NODE)) }

    suspend fun useRepository(): SyncOutcome = run { it.useRepository() }

    suspend fun keepPhone(takeLease: Boolean = false): SyncOutcome = run { it.keepPhone(takeLease) }

    /** The app came to the front: pull, and look for the phone's writes every two minutes while it stays there. */
    fun onForeground() {
        if (!connected) return
        syncNow()
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val e = engine() ?: break
                if (leaseState == LeaseState.MINE && e.localChanges().isNotEmpty()) scheduler.touched()
            }
        }
    }

    /** The app went to the background: what waits is pushed now; the day's backup, when one is due. */
    fun onBackground() {
        ticker?.cancel()
        ticker = null
        if (connected) {
            if (scheduler.pending) scheduler.flush()
            else if (leaseState == LeaseState.MINE) syncNow()
        }
        if (backupDue()) scope.launch { runCatching { backupNow() }.onFailure { log("backup: ${it.message}") } }
    }

    // --- the handover's QR code --------------------------------------------------------------------

    /**
     * Keeps the town's secrets a handover brought ([Handover.keep]); a phone without git connected yet gets the
     * repository they name to pick. Returns the problem, or null.
     */
    suspend fun receive(secrets: HandoverSecrets, replace: Boolean = false): HandoverProblem? {
        val problem = Handover.keep(dataDir, secrets, box, replace)
        if (problem == null) log("handover: the town ${secrets.townKey.fingerprint} kept${secrets.repo?.let { ", repository ${it.full}" } ?: ""}")
        return problem
    }

    /** The repository the last handover named (to suggest in the picker). */
    suspend fun handedRepo(): RepoRef? = box.get(SecretBox.TOWN)?.let { runCatching { syncRead.decodeFromString(HandoverBundle.serializer(), it) }.getOrNull() }
        ?.r?.let { RepoRef.parse(it) }

    // --- the backup --------------------------------------------------------------------------------

    val backupReady: Boolean get() = settings.backup != null && relay.isNotBlank()

    private fun backupDue(): Boolean = backupReady && settings.lastBackup?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?.let { Duration.between(it, clock()) > Duration.ofHours(20) } != false

    /** Sets the backup up with a new recovery code; returns it, to show once. */
    suspend fun setUpCode(): String {
        val code = BackupKeys.newRecoveryCode()
        keepMaster(BackupKeys.masterOfCode(code)!!, "code")
        return code
    }

    suspend fun setUpPassphrase(passphrase: String) = keepMaster(withContext(Dispatchers.Default) { BackupKeys.masterOfPassphrase(passphrase) }, "passphrase")

    private suspend fun keepMaster(master: ByteArray, how: String) {
        box.put(SecretBox.BACKUP_MASTER, Base64.getEncoder().encodeToString(master))
        update { it.copy(backup = how) }
    }

    private suspend fun master(): ByteArray = box.get(SecretBox.BACKUP_MASTER)?.let { Base64.getDecoder().decode(it) } ?: throw BackupError(BackupProblem.NOT_SET_UP)

    private fun backups(master: ByteArray): Backups {
        if (relay.isBlank()) throw BackupError(BackupProblem.NO_RELAY)
        return Backups(dataDir, RelayClient(relay, BackupKeys.signingKey(master), http, clock), master, appVersion, deviceName, clock)
    }

    suspend fun backupNow(): BackupEntry {
        busy = true
        try {
            val entry = withContext(Dispatchers.Default) { backups(master()).backup() }
            update { it.copy(lastBackup = Lease.iso(clock()), lastBackupName = entry.name) }
            return entry
        } finally {
            busy = false
        }
    }

    /** The master of what the learner typed: a recovery code, else a passphrase (null: neither). PBKDF2 takes a moment: off the main thread. */
    suspend fun masterOf(typed: String): Pair<ByteArray, String>? = withContext(Dispatchers.Default) {
        BackupKeys.masterOfCode(typed)?.let { it to "code" }
            ?: typed.trim().takeIf { it.length >= BackupKeys.MIN_PASSPHRASE }?.let { BackupKeys.masterOfPassphrase(it) to "passphrase" }
    }

    /** The backups the phone's own key finds, or ([typed]: a recovery code or passphrase) a new phone's. */
    suspend fun backupList(typed: String? = null): List<BackupEntry> {
        val m = typed?.let { masterOf(it)?.first ?: throw BackupError(BackupProblem.WRONG_KEY) } ?: master()
        return withContext(Dispatchers.Default) { backups(m).list() }
    }

    /**
     * Restores the backup [name] (the newest when null) in place of the phone's data, which is kept beside it. With
     * [typed] (a new phone), the code or passphrase becomes this phone's backup key too. The sync starts anew after.
     */
    suspend fun restore(name: String? = null, typed: String? = null): BackupManifest {
        busy = true
        try {
            val (m, how) = typed?.let { masterOf(it) ?: throw BackupError(BackupProblem.WRONG_KEY) } ?: (master() to (settings.backup ?: "code"))
            val manifest = withContext(Dispatchers.Default) { backups(m).restore(name) }
            if (typed != null) keepMaster(m, how)
            stateFile.delete()
            lease = readLease()
            last = null
            log("backup: restored ${name ?: "the newest"} (${manifest.files} files, ${manifest.created})")
            return manifest
        } finally {
            busy = false
        }
    }

    companion object {
        const val TICK_MS = 120_000L
    }
}
