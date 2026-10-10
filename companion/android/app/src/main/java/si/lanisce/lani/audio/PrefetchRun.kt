package si.lanisce.lani.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import si.lanisce.lani.data.ClipIndex
import si.lanisce.lani.data.Voice
import java.io.File

/**
 * What a prefetch did ([PrefetchRun]), kept in audio/status.json for the settings: when, for which [day]; of the day's
 * [wants], how many are on the phone in their wanted voice ([wanted]), in another of theirs ([other]) or not ([missing]);
 * the files [downloaded] and their [bytes], the lines the node [voiced] for it, whether tomorrow's were got too, whether the
 * cap stopped it ([full]), and why it couldn't run ([problem]).
 */
@Serializable
data class PrefetchStatus(
    val at: Long = 0,
    val day: String = "",
    val wants: Int = 0,
    val wanted: Int = 0,
    val other: Int = 0,
    val missing: Int = 0,
    val downloaded: Int = 0,
    val bytes: Long = 0,
    val voiced: Int = 0,
    val tomorrow: Boolean = false,
    val full: Boolean = false,
    val failed: Int = 0,
    val problem: String? = null,
)

/**
 * Gets the clips of the days ahead ([DayPlan]: today's, then tomorrow's while the cap has room) into the clip cache, without
 * Android, so the tests run it with fakes ([Prefetch] runs it in the background):
 *
 * 1. what the store lacks in a line's wanted voice is asked of the node ([prepare]: POST /voice/prepare, 20 at a time, within
 *    its own limits: not as a live line, a daily budget), today's first; once the node voices none of a batch it isn't asked
 *    again this run;
 * 2. each line's clips are the store's in the first of its voices that has them all ([DayAudio.resolve]);
 * 3. those files are the day's (audio/keep.txt): the cache never drops them to make room;
 * 4. they're got ([ClipFiles]: linked from the car's library when it has them, else downloaded [atOnce] at a time), today's
 *    first, then tomorrow's, while [room] lets them in ([AudioBudget.room]); the clips played longest ago make room
 *    ([AudioFiles.trim]).
 */
class PrefetchRun(
    private val files: AudioFiles,
    private val cap: AudioCap,
    private val index: ClipIndex,
    private val prepare: suspend (List<Pair<String, String>>) -> List<String?>?,
    private val download: suspend (url: String, to: File) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val atOnce: Int = 4,
) {
    /** The store's index with what the node voiced during the run. */
    var updated: ClipIndex = index
        private set

    suspend fun run(days: List<DayList>): PrefetchStatus {
        val today = days.firstOrNull() ?: return PrefetchStatus(at = clock(), problem = "no day")
        var idx = index
        var voiced = 0
        // 1. the node voices what it lacks, today's first
        var asking = true
        for (d in days) {
            if (!asking) break
            val todo = LinkedHashMap<String, Pair<String, String>>()
            for (w in d.wants) for (p in DayAudio.missing(idx, w)) todo.putIfAbsent("${Voice.normalize(p)}|${w.voices.first()}", p to w.voices.first())
            for (batch in todo.values.chunked(PREPARE_AT_ONCE)) {
                val urls = try {
                    prepare(batch)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (urls == null || urls.all { it == null }) {
                    asking = false
                    break
                }
                batch.zip(urls).forEach { (tv, url) ->
                    if (url == null) return@forEach
                    val key = Voice.normalize(tv.first)
                    idx = idx + (key to (idx[key].orEmpty() + (tv.second to url)))
                    voiced++
                }
            }
        }
        updated = idx
        // 2. each line's clips, 3. kept
        val perDay = days.map { d -> d.wants.mapNotNull { DayAudio.resolve(idx, it)?.second }.flatten().distinct() }
        val urlOf = perDay.flatten().associateBy(DayAudio::fileOf)
        files.writeKeep(urlOf.keys)
        // 4. today's files, then tomorrow's, while they fit
        val limit = AudioBudget.limit(cap)
        val road = if (limit == null || AudioBudget.cacheOnly(cap)) emptyList() else files.roadFiles(maxAgeMs = 0)
        val cache = files.cacheFiles(ids = road.isNotEmpty())
        val room = Room(limit?.let { AudioBudget.room(cache, road, it, urlOf.keys) })
        val roadNames = road.mapTo(HashSet()) { it.name }
        var downloaded = 0
        var bytes = 0L
        var failed = 0
        var tomorrow = days.size > 1
        files.clips.mkdirs()
        for ((i, urls) in perDay.withIndex()) {
            val todo = urls.map(DayAudio::fileOf).distinct().filter { !File(files.clips, it).isFile }
            for (batch in todo.chunked(atOnce)) {
                if (room.remaining?.let { it <= 0 } == true) break
                val got = coroutineScope {
                    batch.map { name ->
                        async {
                            val to = File(files.clips, name)
                            val linked = name in roadNames
                            val ok = try {
                                ClipFiles.get(to, listOf(File(files.roadClips, name))) { f -> download(urlOf.getValue(name), f) }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                false
                            }
                            Triple(name, ok, if (ok && !linked) to.length() else 0L)
                        }
                    }.awaitAll()
                }
                for ((name, ok, size) in got) {
                    if (!ok) { failed++; continue }
                    if (!room.take(size)) {
                        File(files.clips, name).delete()
                        continue
                    }
                    downloaded++
                    bytes += size
                }
                if (room.full) break
                if (downloaded > 0 && downloaded % TRIM_EVERY < atOnce) files.trim(cap)
            }
            if (room.full && i == 0) tomorrow = false
            if (room.full) break
        }
        files.trim(cap)
        // how today's lines stand on the phone now
        val stands = today.wants.map { DayAudio.stand(idx, it) { name -> File(files.clips, name).isFile } }
        return PrefetchStatus(
            at = clock(), day = today.day, wants = today.wants.size,
            wanted = stands.count { it == DayAudio.Stand.WANTED }, other = stands.count { it == DayAudio.Stand.OTHER },
            missing = stands.count { it == DayAudio.Stand.MISSING }, downloaded = downloaded, bytes = bytes, voiced = voiced,
            tomorrow = tomorrow && !room.full, full = room.full, failed = failed,
        )
    }

    companion object {
        /** The node's /voice/prepare takes at most this many texts at once. */
        const val PREPARE_AT_ONCE = 20

        /** The cache trims to the cap every this many files. */
        const val TRIM_EVERY = 40
    }
}
