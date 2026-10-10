package si.lanisce.lani.audio

/**
 * A file of Lani's audio on the phone: its [name] in its place, its size, when it was last used (played, or got ready:
 * its modification time) and [id], the file on disk (device and inode) when two names can share it (a clip in the clip
 * cache and in the car's library is one file, hard-linked), else its path.
 */
data class StoredFile(val name: String, val bytes: Long, val used: Long, val id: Any = name)

/**
 * How the cap ([AudioCap]) is kept, pure, so the tests check it. Everything counts once: the clip cache (the day's clips and
 * the clips played lately) and the car's library ("🚗 Za pot"), a file that both have once. The car's library is never
 * dropped (it was got ready on purpose); the clip cache drops its least recently used clips, never the day's
 * ([DayPlan]: today's and tomorrow's, which the prefetch got for the days to come).
 */
object AudioBudget {
    /** The clip cache with the cap off, as it always was. */
    const val OFF_CACHE = si.lanisce.lani.data.Clips.MAX_BYTES

    /** Whatever the car's library takes, the clips played keep at least this much. */
    const val MIN_CACHE = 20L * MB

    /**
     * The most Lani's audio may take under [cap]: null for no limit. With the cap off only the clip cache has a limit, its
     * old one ([cacheOnly]).
     */
    fun limit(cap: AudioCap): Long? = when (cap) {
        AudioCap.OFF -> OFF_CACHE
        AudioCap.UNLIMITED -> null
        else -> cap.bytes
    }

    /** With the cap off the car's library doesn't count, as before: only the clip cache is kept to [OFF_CACHE]. */
    fun cacheOnly(cap: AudioCap): Boolean = cap == AudioCap.OFF

    /** The bytes on disk of [files], a file with two names once. */
    fun unique(files: List<StoredFile>): Long = files.distinctBy { it.id }.sumOf { it.bytes }

    /**
     * The clip cache's files to drop so that everything ([cache] and [others], the car's library) fits in [limit]: the least
     * recently used first, never one [keep] names (the day's), nor one the car's library has too (dropping it frees nothing,
     * and it costs nothing to keep). The cache keeps at least [MIN_CACHE] of its own, whatever the car's library takes.
     */
    fun evict(cache: List<StoredFile>, others: List<StoredFile>, limit: Long, keep: Set<String> = emptySet()): Set<String> {
        val shared = others.mapTo(HashSet()) { it.id }
        val own = cache.filter { it.id !in shared }
        var total = unique(own) + unique(others)
        var mine = unique(own)
        val out = HashSet<String>()
        for (f in own.sortedBy { it.used }) {
            if (total <= limit || mine <= MIN_CACHE) break
            if (f.name in keep) continue
            out += f.name
            total -= f.bytes
            mine -= f.bytes
        }
        return out
    }

    /**
     * How much may still come in under [limit] once everything that may be dropped was: [limit] less the car's library and
     * the clips [keep] names. Negative when even that is over.
     */
    fun room(cache: List<StoredFile>, others: List<StoredFile>, limit: Long, keep: Set<String>): Long {
        val shared = others.mapTo(HashSet()) { it.id }
        val kept = cache.filter { it.name in keep && it.id !in shared }
        return limit - unique(others) - unique(kept)
    }
}

/**
 * What may still come in, counted down as files come (the day's prefetch, the car's getting ready): null for no limit. A file
 * is taken when it fits ([take]), else refused and the rest that wouldn't fit either.
 */
class Room(private var left: Long?) {
    /** The cap was reached: something was refused. */
    var full = false
        private set

    /** [bytes] more fit: they are counted. */
    @Synchronized
    fun take(bytes: Long): Boolean {
        val l = left ?: return true
        if (bytes > l) {
            full = true
            return false
        }
        left = l - bytes
        return true
    }

    /** What is left (null: no limit). */
    val remaining: Long? @Synchronized get() = left
}
