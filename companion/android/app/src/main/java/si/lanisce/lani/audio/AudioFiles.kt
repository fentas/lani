package si.lanisce.lani.audio

import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Lani's audio files on the phone (all in the app's files, not its cache, so Android never clears them behind the learner's
 * back) and how they're kept under the cap ([AudioBudget]):
 * - voice-clips/: the clip cache ([si.lanisce.lani.data.Clips]): the clips played, and the day's clips got ready ahead
 *   ([Prefetch]);
 * - road/clips, road/prompts, road/spoken: the car's library ([si.lanisce.lani.road.RoadStore]);
 * - audio/: the day's plan ([DayPlan]), the clips it keeps (keep.txt) and what the last prefetch did (status.json).
 * A clip in both the cache and the car's library is one file with two names (a hard link), counted once.
 */
class AudioFiles(val filesDir: File) {
    val clips = File(filesDir, CLIPS)
    val road = File(filesDir, "road")
    val dir = File(filesDir, "audio")
    val keepFile = File(dir, "keep.txt")

    /** The car library's clip files (where a cache clip may be linked from, or into). */
    val roadClips = File(road, "clips")

    /** The car library's files, cached for a few minutes: listing thousands of files on every clip played would cost. */
    fun roadFiles(maxAgeMs: Long = ROAD_MAX_AGE_MS, now: Long = System.currentTimeMillis()): List<StoredFile> {
        cachedRoad?.takeIf { (at, path, _) -> path == road.path && now - at < maxAgeMs }?.let { return it.third }
        val files = ROAD_DIRS.flatMap { list(File(road, it), ids = true) }
        cachedRoad = Triple(now, road.path, files)
        return files
    }

    /** The clip cache's files ([ids]: with their file on disk, to tell the ones the car's library has too). */
    fun cacheFiles(ids: Boolean): List<StoredFile> = list(clips, ids) { it.endsWith(".mp3") }

    /** The clips the day's plan keeps (today's and tomorrow's, [Prefetch]): never dropped to make room. */
    fun keep(): Set<String> {
        val at = "${keepFile.path}|${keepFile.lastModified()}|${keepFile.length()}"
        cachedKeep?.takeIf { it.first == at }?.let { return it.second }
        val names = runCatching { keepFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() }.getOrDefault(emptySet())
        cachedKeep = at to names
        return names
    }

    fun writeKeep(names: Collection<String>) {
        dir.mkdirs()
        val tmp = File(dir, "keep.txt.tmp")
        tmp.writeText(names.sorted().joinToString("\n"))
        if (!tmp.renameTo(keepFile)) { keepFile.delete(); tmp.renameTo(keepFile) }
    }

    /**
     * Drops the clip cache's least recently used clips until everything fits under [cap] ([AudioBudget.evict]): with the cap
     * off, the cache alone to its old 50 MB; unlimited, nothing. Returns how many went.
     */
    fun trim(cap: AudioCap): Int {
        val limit = AudioBudget.limit(cap) ?: return 0
        val others = if (AudioBudget.cacheOnly(cap)) emptyList() else roadFiles()
        val cache = cacheFiles(ids = others.isNotEmpty())
        val drop = AudioBudget.evict(cache, others, limit, keep())
        drop.forEach { File(clips, it).delete() }
        return drop.size
    }

    /** Everything on the phone now: the clip cache and the car's library, each file once. */
    fun usage(): Usage {
        val road = roadFiles(maxAgeMs = 0)
        val cache = cacheFiles(ids = road.isNotEmpty())
        val keep = keep()
        return Usage(
            total = AudioBudget.unique(cache + road),
            clips = cache.size,
            cache = AudioBudget.unique(cache),
            day = AudioBudget.unique(cache.filter { it.name in keep }),
            road = AudioBudget.unique(road),
        )
    }

    /** Lani's audio on the phone: [total] bytes (each file once); the clip cache ([clips] files, [cache] bytes, of them [day] the day's) and the car's library. */
    data class Usage(val total: Long, val clips: Int, val cache: Long, val day: Long, val road: Long)

    /** The car's library changed (getting ready wrote to it): listed again the next time. */
    fun forgetRoad() {
        cachedRoad = null
    }

    companion object {
        const val CLIPS = "voice-clips"
        private val ROAD_DIRS = listOf("clips", "prompts", "spoken")
        private const val ROAD_MAX_AGE_MS = 10 * 60_000L

        @Volatile
        private var cachedRoad: Triple<Long, String, List<StoredFile>>? = null

        @Volatile
        private var cachedKeep: Pair<String, Set<String>>? = null

        /** The files of [dir] whose names [accept] takes ([ids]: each with its file on disk). */
        fun list(dir: File, ids: Boolean, accept: (String) -> Boolean = { !it.endsWith(".part") && !it.endsWith(".tmp") && !it.endsWith(".link") }): List<StoredFile> =
            dir.listFiles().orEmpty().filter { it.isFile && accept(it.name) }.map { f ->
                StoredFile(f.name, f.length(), f.lastModified(), if (ids) fileId(f) else f.path)
            }

        /** The file on disk (device and inode) behind [f], so two names of one file count once; else its path. */
        fun fileId(f: File): Any =
            runCatching { Files.readAttributes(f.toPath(), BasicFileAttributes::class.java).fileKey() }.getOrNull() ?: f.absolutePath

        /**
         * [from] under the name [to] too: a hard link (one file on disk, nothing copied), else a copy where the file system
         * can't link. Through a ".part" name, so a half-written file never counts as there. True once [to] is there.
         */
        fun link(from: File, to: File): Boolean {
            if (to.isFile) return true
            if (!from.isFile) return false
            to.parentFile?.mkdirs()
            val part = File(to.path + ".link")
            part.delete()
            val linked = runCatching { Files.createLink(part.toPath(), from.toPath()) }.isSuccess ||
                runCatching { Files.copy(from.toPath(), part.toPath(), StandardCopyOption.REPLACE_EXISTING) }.isSuccess
            if (!linked) return false
            if (!part.renameTo(to)) {
                part.delete()
                return to.isFile
            }
            return true
        }
    }
}

/**
 * Getting a clip of the node's onto the phone, shared by everyone who needs one: a line played and the day's prefetch (the
 * clip cache), and the car's getting ready (its library). A clip already in another place ([from]) is linked, not downloaded
 * again; a clip on its way for someone else is waited for, then linked. So the car and the prefetch never fetch one twice.
 */
object ClipFiles {
    private class Fetch(val to: File, val done: CompletableDeferred<Boolean> = CompletableDeferred())

    private val fetching = HashMap<String, Fetch>()

    /**
     * Clip [to] (named as the node names it, `<sha1>.mp3`): there already, linked from the first of [from] that has it, or
     * [download]ed into it (the caller says how: counted or not, [si.lanisce.lani.road.RoadPrepService]). True once it's there.
     * [download] writes the file whole or throws.
     */
    suspend fun get(to: File, from: List<File>, download: suspend (File) -> Unit): Boolean {
        if (to.isFile) return true
        from.firstOrNull { it.isFile }?.let { if (AudioFiles.link(it, to)) return true }
        val name = to.name
        val (mine, other) = synchronized(fetching) {
            val f = fetching[name]
            if (f != null) null to f else Fetch(to).also { fetching[name] = it } to null
        }
        if (other != null) {
            // someone else is getting it: wait for theirs, then link it (or fetch it after all, when theirs failed)
            if (other.done.await() && AudioFiles.link(other.to, to)) return true
            return get(to, from, download)
        }
        var ok = false
        try {
            to.parentFile?.mkdirs()
            download(to)
            ok = to.isFile && to.length() > 0
            return ok
        } finally {
            synchronized(fetching) { if (fetching[name] === mine) fetching.remove(name) }
            mine!!.done.complete(ok)
        }
    }
}
