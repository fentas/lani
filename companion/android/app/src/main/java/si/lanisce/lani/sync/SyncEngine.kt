package si.lanisce.lani.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The git blob id of [bytes]: SHA-1 of "blob <size>\0" and the bytes, as git and GitHub name a file's content. */
fun blobSha(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-1")
    md.update("blob ${bytes.size}\u0000".toByteArray())
    md.update(bytes)
    return md.digest().joinToString("") { "%02x".format(it) }
}

/** A file's size and modification time with its blob id: a file that kept both isn't read again. */
@Serializable
data class FileStat(val size: Long, val mtime: Long, val sha: String)

/** The phone's version of the data, kept beside the repository's on [branch] when both changed [files]. */
@Serializable
data class Divergence(val branch: String? = null, val files: List<String> = emptyList(), val at: String = "", val remote: String? = null)

/**
 * What the sync knows between runs (<filesDir>/lani/sync/state.json): the [repo], the commit both sides last agreed on
 * ([base], its tree [baseTreeSha] and files [baseTree]: path → blob id), the branch's ETag, the stat cache, when it last
 * synced, and a [diverged] state that waits for the learner.
 */
@Serializable
data class SyncState(
    val repo: String? = null,
    val branch: String = "main",
    val base: String? = null,
    val baseTreeSha: String? = null,
    val baseTree: Map<String, String> = emptyMap(),
    val etag: String? = null,
    val stats: Map<String, FileStat> = emptyMap(),
    val lastSync: String? = null,
    val lastPull: String? = null,
    val lastPush: String? = null,
    val diverged: Divergence? = null,
)

/** How a sync went. */
sealed interface SyncOutcome {
    /** Synced: the files [pulled] from the repository and [pushed] to it ([commit]); [skipped]: too large to sync. */
    data class Done(val pulled: List<String> = emptyList(), val pushed: List<String> = emptyList(), val commit: String? = null, val skipped: List<String> = emptyList()) : SyncOutcome

    /** Both sides changed [files]: nothing was overwritten; the phone's version is on the repository's [branch] too. */
    data class Diverged(val branch: String?, val files: List<String>) : SyncOutcome

    /** The phone changed [files] while it doesn't hold the lease: they stay on the phone, not pushed. */
    data class NotHolder(val files: List<String>, val state: LeaseState) : SyncOutcome

    /** The lease can't be taken (or handed over) now: [state] says why. */
    data class LeaseRefused(val state: LeaseState, val lease: LeaseRead) : SyncOutcome

    /** It failed ([message]); [retryAt]: GitHub's rate limit is used up until then. Nothing was lost. */
    data class Failed(val message: String, val retryAt: Instant? = null) : SyncOutcome
}

/**
 * The phone's data directory (<filesDir>/lani/data, the data repository's layout) synced with a repository on GitHub,
 * through the Git Data API ([GitHub]): docs/plans/05-built/git-sync.md, "The sync".
 *
 * - **Fast-forward only.** The repository's branch only ever moves on from where the phone last saw it. A change of the
 *   repository's is pulled when the phone changed nothing, or other files; the phone's changes are pushed on top.
 * - **Never lose a change.** When both sides changed the same file (or the repository's history was rewritten), nothing
 *   is overwritten: the phone's version goes to a branch of its own (lani/phone-<device>-<time>), the data on the phone
 *   stays as it is, and the sync waits for the learner ([useRepository], [keepPhone]).
 * - **The lease decides who pushes** ([Lease]): the phone pushes its changes only while it holds the lease; it always
 *   pulls. It takes the lease ([take]) and hands it over ([handOver]) with a commit of app/lease.json.
 * - **Never the private files** ([DataFilter]), and no file over [GitHub.MAX_FILE].
 */
class SyncEngine(
    val dataDir: File,
    private val stateFile: File,
    private val gh: GitHub,
    val repo: RepoRef,
    private val me: Party,
    private val clock: () -> Instant = Instant::now,
    private val log: (String) -> Unit = {},
    /** Told the files a pull changed on the phone, so what reads them reads them again. */
    private val onPulled: (List<String>) -> Unit = {},
) {
    private val mutex = Mutex()

    fun state(): SyncState = runCatching { syncRead.decodeFromString(SyncState.serializer(), stateFile.readText()) }.getOrNull()
        ?.takeIf { it.repo == repo.full && it.branch == repo.branch } ?: SyncState(repo = repo.full, branch = repo.branch)

    private fun save(s: SyncState) {
        stateFile.parentFile?.mkdirs()
        val tmp = File(stateFile.path + ".tmp")
        tmp.writeText(syncJson.encodeToString(SyncState.serializer(), s))
        if (!tmp.renameTo(stateFile)) throw IOException("could not save ${stateFile.name}")
    }

    /** The lease as the phone's copy of the data has it. */
    fun lease(): LeaseRead = Lease.parse(File(dataDir, Lease.FILE).takeIf { it.isFile }?.readText())

    /** The files the phone changed since the last sync (a path, or a path that went). */
    suspend fun localChanges(): List<String> = withContext(Dispatchers.IO) {
        val st = state()
        diff(st.baseTree, scan(st).files).keys.sorted()
    }

    /** Pulls, and pushes what the phone changed while it holds the lease. */
    suspend fun sync(): SyncOutcome = mutex.withLock { guarded { syncOnce(retry = true) } }

    /**
     * Takes the lease: pulls first, then takes it when it is the phone's to take (handed over to it, free, run out) or
     * [force]d (the other side is lost: it stops writing at its next sync). Commits app/lease.json and pushes.
     */
    suspend fun take(force: Boolean = false): SyncOutcome = mutex.withLock {
        guarded {
            when (val r = syncOnce(retry = true)) {
                is SyncOutcome.Done, is SyncOutcome.NotHolder -> Unit
                else -> return@guarded r
            }
            val now = clock()
            val lease = lease()
            val st = state()
            val state = lease.stateFor(me, now, hasData = st.baseTree.keys.any { !it.startsWith(".") && it != Lease.FILE })
            if (!state.takeable && !force) return@guarded SyncOutcome.LeaseRefused(state, lease)
            if (state == LeaseState.MINE) return@guarded SyncOutcome.Done()
            writeLocal(Lease.FILE, Lease.held(me, now).toJson().toByteArray())
            val how = if (state.takeable) "" else " (without a handover)"
            pushLocal(state(), "lease: ${me.who()} takes the data$how", force = true)
        }
    }

    /** Hands the lease over to [to] (the node): pushes the phone's changes, then the lease offered to it. */
    suspend fun handOver(to: Party): SyncOutcome = mutex.withLock {
        guarded {
            val lease = lease()
            val state = lease.stateFor(me, clock())
            if (state != LeaseState.MINE) return@guarded SyncOutcome.LeaseRefused(state, lease)
            when (val r = syncOnce(retry = true)) {
                is SyncOutcome.Done -> Unit
                else -> return@guarded r
            }
            writeLocal(Lease.FILE, Lease.offered(to, me, clock()).toJson().toByteArray())
            pushLocal(state(), "lease: handed over to ${to.who()}", force = true)
        }
    }

    /**
     * After a divergence: the repository's version replaces the phone's (whose version stays on its branch in the
     * repository). Files the repository doesn't have go from the phone, as the repository's state says.
     */
    suspend fun useRepository(): SyncOutcome = mutex.withLock {
        guarded {
            val st = state()
            val head = (gh.ref(repo) as? RefState.At) ?: return@guarded SyncOutcome.Failed("the repository has no branch ${repo.branch}")
            val commit = gh.commit(repo, head.sha)
            val remote = wanted(gh.tree(repo, commit.tree))
            val scanned = withContext(Dispatchers.IO) { scan(st) }
            val pulled = applyChanges(diff(scanned.files, remote), scanned)
            save(scanned.state(st).copy(base = head.sha, baseTreeSha = commit.tree, baseTree = remote, etag = head.etag, diverged = null, lastSync = now(), lastPull = now()))
            if (pulled.isNotEmpty()) onPulled(pulled)
            SyncOutcome.Done(pulled = pulled)
        }
    }

    /**
     * After a divergence: the phone's version goes on top of the repository's (a fast-forward; the repository's stays in
     * its history). Only while the phone holds the lease, or takes it now ([takeLease]: when it is the phone's to take,
     * or the learner confirmed a takeover).
     */
    suspend fun keepPhone(takeLease: Boolean = false): SyncOutcome = mutex.withLock {
        guarded {
            val st = state()
            val head = (gh.ref(repo) as? RefState.At) ?: return@guarded SyncOutcome.Failed("the repository has no branch ${repo.branch}")
            val commit = gh.commit(repo, head.sha)
            val remote = wanted(gh.tree(repo, commit.tree))
            val theirs = remote[Lease.FILE]?.let { Lease.parse(gh.blob(repo, it).decodeToString()) } ?: LeaseRead.None
            val state = theirs.stateFor(me, clock(), hasData = remote.keys.any { it != Lease.FILE })
            if (state != LeaseState.MINE) {
                if (!takeLease) return@guarded SyncOutcome.LeaseRefused(state, theirs)
                writeLocal(Lease.FILE, Lease.held(me, clock()).toJson().toByteArray())
            }
            // the repository's state as the base: what the phone has otherwise is its change
            pushLocal(st.copy(base = head.sha, baseTreeSha = commit.tree, baseTree = remote, diverged = null), "phone: ${me.who()} keeps its version", force = true)
        }
    }

    // --- one sync ----------------------------------------------------------------------------------

    private suspend fun guarded(block: suspend () -> SyncOutcome): SyncOutcome = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RateLimited) {
        SyncOutcome.Failed(e.message ?: "rate limited", e.until)
    } catch (e: Exception) { // a network error, an answer that isn't JSON, a file that can't be written: nothing is lost
        log("sync: ${e.message ?: e}")
        SyncOutcome.Failed(e.message ?: e.toString())
    }

    private fun now() = Lease.iso(clock())

    private suspend fun syncOnce(retry: Boolean): SyncOutcome {
        val st = state()
        st.diverged?.let { return SyncOutcome.Diverged(it.branch, it.files) }
        val scanned = withContext(Dispatchers.IO) { scan(st) }
        val head = gh.ref(repo, st.etag.takeIf { st.base != null })
        val remoteSha = when (head) {
            is RefState.At -> head.sha
            RefState.NotModified -> st.base
            RefState.Missing, RefState.EmptyRepository -> null
        }
        val etag = (head as? RefState.At)?.etag ?: st.etag
        val local = diff(st.baseTree, scanned.files)

        if (st.base == null) return firstSync(st.copy(etag = etag), scanned, head, remoteSha)
        if (remoteSha == null) return SyncOutcome.Failed("the repository's branch ${repo.branch} is gone: nothing was changed on the phone")

        if (remoteSha == st.base) {
            if (local.isEmpty()) {
                save(scanned.state(st).copy(etag = etag, lastSync = now()))
                return SyncOutcome.Done(skipped = scanned.skipped)
            }
            return pushIfHolder(scanned.state(st).copy(etag = etag), local.keys.sorted(), scanned.skipped)
        }

        // the repository went on: from the phone's base, or (rewritten) not
        if (gh.compare(repo, st.base, remoteSha) != "ahead") return keepBoth(st, scanned, local.keys.sorted(), remoteSha, rewritten = true)
        val commit = gh.commit(repo, remoteSha)
        val remoteTree = wanted(gh.tree(repo, commit.tree))
        val theirs = diff(st.baseTree, remoteTree)
        val conflicts = theirs.keys.filter { it in local && local[it] != theirs[it] }.sorted()
        if (conflicts.isNotEmpty()) return keepBoth(st, scanned, conflicts, remoteSha, rewritten = false)
        // what the phone changed itself is kept; the rest comes from the repository (a file both changed alike is no conflict)
        val pulled = applyChanges(theirs.filterKeys { it !in local }, scanned)
        val after = scanned.state(st).copy(base = remoteSha, baseTreeSha = commit.tree, baseTree = remoteTree, etag = etag, lastSync = now(), lastPull = now())
        save(after)
        if (pulled.isNotEmpty()) onPulled(pulled)
        val mine = local.filter { (p, sha) -> remoteTree[p] != sha }
        if (mine.isEmpty()) return SyncOutcome.Done(pulled = pulled, skipped = scanned.skipped)
        return when (val r = pushIfHolder(after, mine.keys.sorted(), scanned.skipped)) {
            is SyncOutcome.Done -> r.copy(pulled = pulled)
            else -> r
        }.also { if (it is SyncOutcome.Failed && retry) log("sync: ${it.message}") }
    }

    /** The first sync with this repository: an empty one gets the phone's data, an empty phone the repository's. */
    private suspend fun firstSync(st: SyncState, scanned: Scan, head: RefState, remoteSha: String?): SyncOutcome {
        if (remoteSha == null) {
            if (scanned.files.isEmpty()) {
                save(scanned.state(st).copy(lastSync = now()))
                return SyncOutcome.Done()
            }
            // a new repository: the phone's data is its first commit, and the lease the phone's (it was free)
            val lease = lease()
            if (!lease.stateFor(me, clock(), hasData = false).takeable) return SyncOutcome.LeaseRefused(lease.stateFor(me, clock(), false), lease)
            if (lease.stateFor(me, clock()) != LeaseState.MINE) writeLocal(Lease.FILE, Lease.held(me, clock()).toJson().toByteArray())
            var base = st
            if (head == RefState.EmptyRepository) {
                // the Git Data API refuses to work on an empty repository: its first commit through the contents API
                val first = Lease.FILE
                val bytes = File(dataDir, first).readBytes()
                val sha = gh.firstFile(repo, first, bytes, "phone: ${me.who()} starts the data repository")
                val c = gh.commit(repo, sha)
                base = st.copy(base = sha, baseTreeSha = c.tree, baseTree = mapOf(first to blobSha(bytes)))
            }
            return pushLocal(base, "phone: ${me.who()}: the learner's data", force = true, createBranch = head == RefState.Missing)
        }
        val commit = gh.commit(repo, remoteSha)
        val remoteTree = wanted(gh.tree(repo, commit.tree))
        val differ = diff(scanned.files, remoteTree)
        if (scanned.files.isEmpty() || differ.isEmpty()) {
            val pulled = applyChanges(differ, scanned)
            save(scanned.state(st).copy(base = remoteSha, baseTreeSha = commit.tree, baseTree = remoteTree, lastSync = now(), lastPull = now()))
            if (pulled.isNotEmpty()) onPulled(pulled)
            return SyncOutcome.Done(pulled = pulled, skipped = scanned.skipped)
        }
        // both have data, and it differs: keep both, and let the learner choose
        return keepBoth(st.copy(base = null), scanned, differ.keys.sorted(), remoteSha, rewritten = false)
    }

    private suspend fun pushIfHolder(st: SyncState, files: List<String>, skipped: List<String>): SyncOutcome {
        val state = lease().stateFor(me, clock())
        if (state != LeaseState.MINE) {
            save(st.copy(lastSync = now()))
            return SyncOutcome.NotHolder(files, state)
        }
        return when (val r = pushLocal(st, message(files), force = false)) {
            is SyncOutcome.Done -> r.copy(skipped = skipped)
            else -> r
        }
    }

    /**
     * Commits what the phone has that [st]'s base hasn't on top of the base, and moves the branch there (fast-forward).
     * [force]: also the lease's own commits (the phone doesn't hold it yet, or no more). A branch that went on meanwhile:
     * the next sync pulls first.
     */
    private suspend fun pushLocal(st: SyncState, message: String, force: Boolean, createBranch: Boolean = false): SyncOutcome {
        var scanned = withContext(Dispatchers.IO) { scan(st) }
        // the holder renews its lease with a push, once a day
        lease().lease?.renewedBy(me, clock())?.let {
            writeLocal(Lease.FILE, it.toJson().toByteArray())
            scanned = withContext(Dispatchers.IO) { scan(st) }
        }
        val changes = diff(st.baseTree, scanned.files)
        if (changes.isEmpty()) {
            save(scanned.state(st).copy(lastSync = now()))
            return SyncOutcome.Done(skipped = scanned.skipped)
        }
        if (!force && lease().stateFor(me, clock()) != LeaseState.MINE) return SyncOutcome.NotHolder(changes.keys.sorted(), lease().stateFor(me, clock()))
        val tree = gh.createTree(repo, st.baseTreeSha, changes.map { (path, sha) -> edit(path, sha) })
        val commit = gh.createCommit(repo, message, tree, listOfNotNull(st.base))
        val moved = if (createBranch || st.base == null && st.baseTreeSha == null) gh.createBranch(repo, repo.branch, commit) else gh.moveBranch(repo, commit)
        if (!moved) return SyncOutcome.Failed("the repository went on meanwhile: the next sync pulls it first")
        val tree2 = st.baseTree.toMutableMap().apply { changes.forEach { (p, sha) -> if (sha == null) remove(p) else put(p, sha) } }
        save(scanned.state(st).copy(base = commit, baseTreeSha = tree, baseTree = tree2, etag = null, lastSync = now(), lastPush = now()))
        log("sync: pushed ${changes.size} file(s) as $commit")
        return SyncOutcome.Done(pushed = changes.keys.sorted(), commit = commit, skipped = scanned.skipped)
    }

    /** The phone's version of [files] onto a branch of its own; the phone's data stays, and the sync waits for the learner. */
    private suspend fun keepBoth(st: SyncState, scanned: Scan, files: List<String>, remoteSha: String, rewritten: Boolean): SyncOutcome {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(clock())
        val branch = "lani/phone-${me.device.ifBlank { "phone" }.replace(Regex("[^A-Za-z0-9_-]"), "")}-$stamp"
        val changes = diff(st.baseTree, scanned.files)
        val kept = if (changes.isEmpty()) null else {
            val tree = gh.createTree(repo, st.baseTreeSha, changes.map { (path, sha) -> edit(path, sha) })
            val commit = gh.createCommit(repo, "phone: ${me.who()}'s version, kept beside the repository's (${files.take(5).joinToString()})", tree, listOfNotNull(st.base))
            gh.createBranch(repo, branch, commit)
            branch
        }
        val d = Divergence(kept, files, now(), remoteSha)
        save(scanned.state(st).copy(diverged = d, lastSync = now()))
        log("sync: both sides changed ${files.joinToString()}${if (rewritten) " (the repository's history was rewritten)" else ""}; the phone's version is on $kept")
        return SyncOutcome.Diverged(kept, files)
    }

    // --- files -------------------------------------------------------------------------------------

    /** The phone's files that sync (path → blob id), the stat cache, and those too large to sync. */
    private inner class Scan(val files: Map<String, String>, val stats: Map<String, FileStat>, val skipped: List<String>) {
        fun state(st: SyncState) = st.copy(stats = stats)
    }

    private fun scan(st: SyncState): Scan {
        val filter = DataFilter.of(dataDir)
        val files = HashMap<String, String>()
        val stats = HashMap<String, FileStat>()
        val skipped = mutableListOf<String>()
        if (!dataDir.isDirectory) return Scan(files, stats, skipped)
        val root = dataDir.toPath()
        Files.walk(root).use { paths ->
            for (p in paths) {
                if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) continue
                val rel = root.relativize(p).joinToString("/")
                if (!filter.pushes(rel)) continue
                val f = p.toFile()
                val size = f.length()
                if (size > GitHub.MAX_FILE) {
                    skipped += rel
                    continue
                }
                val mtime = f.lastModified()
                val cached = st.stats[rel]?.takeIf { it.size == size && it.mtime == mtime }
                val sha = cached?.sha ?: blobSha(f.readBytes())
                files[rel] = sha
                stats[rel] = FileStat(size, mtime, sha)
            }
        }
        return Scan(files, stats, skipped.sorted())
    }

    /**
     * The repository's files the phone syncs (path → blob id): what its filter lets through. Others (a file the node keeps
     * out of the phone's reach) are neither pulled nor ever removed by a push: new trees are built on the repository's.
     */
    private fun wanted(tree: List<TreeFile>): Map<String, String> {
        val filter = DataFilter.of(dataDir)
        return tree.filter { filter.pushes(it.path) }.associate { it.path to it.sha }
    }

    /** What [to] has that [from] hasn't: path → its blob id, or null for a path that went. */
    private fun diff(from: Map<String, String>, to: Map<String, String>): Map<String, String?> {
        val out = HashMap<String, String?>()
        for ((p, sha) in to) if (from[p] != sha) out[p] = sha
        for (p in from.keys) if (p !in to) out[p] = null
        return out
    }

    /** A tree edit for [path]: its text inline (no blob request) when it is UTF-8, else a blob of its own. */
    private suspend fun edit(path: String, sha: String?): TreeEdit {
        if (sha == null) return TreeEdit(path)
        val bytes = withContext(Dispatchers.IO) { File(dataDir, path).readBytes() }
        val text = bytes.decodeToString()
        if (text.encodeToByteArray().contentEquals(bytes) && bytes.size < INLINE_MAX) return TreeEdit(path, content = text)
        return TreeEdit(path, sha = gh.createBlob(repo, bytes))
    }

    /** Writes the repository's [changes] into the phone's data directory (downloads, removals); the paths changed. */
    private suspend fun applyChanges(changes: Map<String, String?>, scanned: Scan): List<String> {
        val done = mutableListOf<String>()
        val filter = DataFilter.of(dataDir)
        val root = dataDir.canonicalFile.path + File.separator
        for ((path, sha) in changes.toSortedMap()) {
            if (!safe(path, filter, root)) continue
            if (sha == null) {
                withContext(Dispatchers.IO) { File(dataDir, path).delete() }
            } else {
                val bytes = gh.blob(repo, sha)
                if (blobSha(bytes) != sha) throw IOException("GitHub sent $path with the wrong content")
                writeLocal(path, bytes)
            }
            done += path
        }
        return done
    }

    /** A path from the repository that stays inside the data directory and isn't private on the phone. */
    private fun safe(path: String, filter: DataFilter, root: String): Boolean {
        if (path.startsWith("/") || path.split('/').any { it == ".." || it == "." || it.isEmpty() }) return false
        return File(dataDir, path).canonicalFile.path.startsWith(root) && filter.pushes(path)
    }

    private suspend fun writeLocal(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val f = File(dataDir, path)
        f.parentFile?.mkdirs()
        val tmp = File(f.path + DataFilter.TEMP_SUFFIX)
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) throw IOException("could not write $path")
    }

    private fun message(files: List<String>): String {
        val head = "phone: ${me.who()}: ${files.size} file${if (files.size == 1) "" else "s"}"
        return head + "\n\n" + files.take(20).joinToString("\n") + if (files.size > 20) "\n…" else ""
    }

    companion object {
        /** A text file up to this size goes inline in the new tree; a larger one (or a binary) as a blob of its own. */
        const val INLINE_MAX = 1024 * 1024
    }
}
