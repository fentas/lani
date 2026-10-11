package si.lanisce.lani.audio

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import si.lanisce.lani.data.ClipIndex
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A re-voiced line's earlier clip, kept playing until its new one is on the phone (companion/README.md, "Offline on the
 * phone"). The node re-voices clips (a narrator's local clip gets its ElevenLabs take as credits allow), and a new take has
 * a new file name (`<sha1>.mp3`), so a line's URL in the index changes while the phone still has the old file. Offline the
 * line would then fall to the phone's own voice; with this the old file plays until the new one is here, then goes.
 *
 * The earlier clips are kept like the index, phrase key → voice → URL, in the clip cache's `previous.json`: an entry for
 * each line whose URL changed while the file of an earlier URL was on the phone (a chain of changes keeps the newest such
 * file). An entry goes once the line's new file is on the phone (its old file deleted, unless the index still plays it
 * elsewhere or it played just now), once its old file is gone (the cap dropped it), or when the line is re-recorded on
 * request ([si.lanisce.lani.data.ReRecorder] drops the old take on purpose). The helpers here are pure, for the tests;
 * the store below reads and writes the file for the app ([Clips]) and the day's prefetch ([PrefetchRun]) alike.
 */
object PreviousClips {
    /** The earlier clips' file in the clip cache, next to [Clips.INDEX]. */
    const val FILE = "previous.json"

    /** A file played this recently may be playing still: it isn't deleted yet (a later pass deletes it). */
    const val BUSY_MS = 60_000L

    /** The files to play a text with: per phrase its clip ([urls], in order) and of them the earlier ones standing in ([old]). */
    data class Pick(val urls: List<String>, val old: List<String> = emptyList()) {
        /** An earlier clip stands in for one phrase or more: the new one is worth getting. */
        val stale: Boolean get() = old.isNotEmpty()
    }

    /** What a pass over the earlier clips leaves: the entries still needed, and the files to delete. */
    data class Settled(val previous: ClipIndex, val delete: Set<String>)

    /** A clip URL's file name, as the phone keeps it. */
    fun fileOf(url: String): String = DayAudio.fileOf(url)

    /**
     * The clips to play [text] in [voice] from the phone ([has]: a file name is on the phone): each phrase's clip in
     * [index] when its file is here, else its earlier one in [previous] when that one is; null when a phrase has neither,
     * or [index] has no clips of [text] in [voice] ([Clips.resolve]).
     */
    fun pick(index: ClipIndex, previous: ClipIndex, text: String, voice: String, has: (String) -> Boolean): Pick? {
        val keys = Clips.keys(index, text, voice) ?: return null
        val old = mutableListOf<String>()
        val urls = keys.map { k ->
            val now = index.getValue(k).getValue(voice)
            if (has(fileOf(now))) now else previous[k]?.get(voice)?.takeIf { has(fileOf(it)) }?.also(old::add) ?: return null
        }
        return Pick(urls, old)
    }

    /**
     * The earlier clips once the index changed from [olds] (newest first: the copy on disk, the app's) to [new]: for each
     * phrase and voice of [new], the newest of its earlier clips whose file is on the phone ([has]), from [olds] and then
     * [previous] (so a chain of changes keeps the newest file here). Entries of a line [new] no longer has, back at their
     * earlier clip, or whose files are all gone are not kept.
     */
    fun supersede(olds: List<ClipIndex>, new: ClipIndex, previous: ClipIndex, has: (String) -> Boolean): ClipIndex {
        val out = LinkedHashMap<String, Map<String, String>>()
        for ((key, voices) in new) {
            val kept = LinkedHashMap<String, String>()
            for ((voice, url) in voices) {
                val now = fileOf(url)
                val earlier = olds.mapNotNull { it[key]?.get(voice) } + listOfNotNull(previous[key]?.get(voice))
                earlier.firstOrNull { val f = fileOf(it); f != now && has(f) }?.let { kept[voice] = it }
            }
            if (kept.isNotEmpty()) out[key] = kept
        }
        return out
    }

    /**
     * A pass over [previous] against [index]: an entry whose new file is on the phone ([has]) is done, and its old file is
     * deleted unless [index] (or [referenced], another index's files) still plays it, or it was [busy] (played just now:
     * the entry stays for a later pass); an entry whose old file is gone (the cap dropped it), whose line [index] no longer
     * has, or back at its earlier clip is forgotten. [only]: just the entries of these phrase keys (a line just fetched).
     */
    fun settle(
        index: ClipIndex,
        previous: ClipIndex,
        has: (String) -> Boolean,
        busy: (String) -> Boolean = { false },
        referenced: Set<String> = emptySet(),
        only: Set<String>? = null,
    ): Settled {
        val playing = HashSet(referenced)
        for (v in index.values) v.values.mapTo(playing, ::fileOf)
        val left = LinkedHashMap<String, MutableMap<String, String>>()
        val delete = HashSet<String>()
        for ((key, voices) in previous) for ((voice, url) in voices) {
            if (only != null && key !in only) {
                left.getOrPut(key) { LinkedHashMap() }[voice] = url
                continue
            }
            val now = index[key]?.get(voice)?.let(::fileOf) ?: continue
            val old = fileOf(url)
            if (old == now || !has(old)) continue
            if (!has(now) || (old !in playing && busy(old))) {
                left.getOrPut(key) { LinkedHashMap() }[voice] = url
                continue
            }
            if (old !in playing) delete += old
        }
        // a file still standing in for another line stays
        for (v in left.values) for (u in v.values) delete -= fileOf(u)
        return Settled(left, delete)
    }

    /**
     * The index changed from [olds] to [new]: [supersede], then [settle]; an earlier file the chain passed over for a newer
     * one on the phone goes too (by [settle]'s rules). A line [new] no longer has keeps its file (the cap drops it in time).
     */
    fun swap(
        olds: List<ClipIndex>,
        new: ClipIndex,
        previous: ClipIndex,
        has: (String) -> Boolean,
        busy: (String) -> Boolean = { false },
        referenced: Set<String> = emptySet(),
    ): Settled {
        val s = settle(new, supersede(olds, new, previous, has), has, busy, referenced)
        val kept = s.previous.values.flatMapTo(HashSet()) { v -> v.values.map(::fileOf) }
        val playing = HashSet(referenced).also { p -> for (v in new.values) v.values.mapTo(p, ::fileOf) }
        val passed = HashSet<String>()
        for ((key, voices) in previous) for ((voice, url) in voices) {
            val f = fileOf(url)
            if (new[key]?.get(voice) != null && f !in kept && f !in playing && has(f) && !busy(f)) passed += f
        }
        return Settled(s.previous, s.delete + passed)
    }

    /** [previous] without the entries whose files are [gone] (the cap dropped them). */
    fun without(previous: ClipIndex, gone: Set<String>): ClipIndex =
        previous.mapValues { (_, v) -> v.filterValues { fileOf(it) !in gone } }.filterValues { it.isNotEmpty() }

    /** [previous] without the entry of phrase [key] in [voice] (re-recorded on request), and its earlier URL. */
    fun without(previous: ClipIndex, key: String, voice: String): Pair<ClipIndex, String?> {
        val url = previous[key]?.get(voice) ?: return previous to null
        val rest = previous[key].orEmpty() - voice
        return (if (rest.isEmpty()) previous - key else previous + (key to rest)) to url
    }

    /**
     * The earlier clips standing in for [w] on the phone (its new ones not all here yet), as file names: the day keeps them
     * ([AudioFiles.keep]) with its clips, so the cap doesn't drop them, each line's audio counted once.
     */
    fun standIns(index: ClipIndex, previous: ClipIndex, w: DayWant, has: (String) -> Boolean): List<String> {
        if (previous.isEmpty()) return emptyList()
        val (voice, _) = DayAudio.resolve(index, w) ?: return emptyList()
        return pick(index, previous, w.text, voice, has)?.old?.map(::fileOf).orEmpty()
    }

    // --- the store: previous.json in the clip cache ------------------------------------------------------------------

    /** The app's index now ([Clips.index]), when the app is open: the prefetch deletes no file it still plays. */
    @Volatile
    var appIndex: (() -> ClipIndex)? = null

    private val lock = Any()
    private val loaded = ConcurrentHashMap<String, ClipIndex>()
    private val SERIALIZER = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

    /** The earlier clips of the clip cache [dir], read once (all writes go through [update]). */
    fun read(dir: File): ClipIndex = loaded[dir.path] ?: synchronized(lock) {
        loaded.getOrPut(dir.path) { runCatching { Clips.parseIndex(File(dir, FILE).readText()) }.getOrDefault(emptyMap()) }
    }

    /** What [read] has read already: no disk (for the main thread; empty before the first read). */
    fun known(dir: File): ClipIndex = loaded[dir.path] ?: emptyMap()

    /** Changes the earlier clips of [dir] under the one lock (the app and the prefetch alike), written only when changed. */
    fun <T> update(dir: File, change: (ClipIndex) -> Pair<ClipIndex, T>): T = synchronized(lock) {
        val cur = read(dir)
        val (next, out) = change(cur)
        if (next != cur) {
            write(dir, next)
            loaded[dir.path] = next
        }
        out
    }

    private fun write(dir: File, previous: ClipIndex) {
        val f = File(dir, FILE)
        if (previous.isEmpty()) {
            f.delete()
            return
        }
        dir.mkdirs()
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(json.encodeToString(SERIALIZER, previous))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    /** Deletes [names] from the clip cache [dir]; their sizes (what came free). */
    private fun delete(dir: File, names: Set<String>): Map<String, Long> = names.associateWith { n ->
        val f = File(dir, n)
        val size = f.length()
        if (f.delete()) size else 0L
    }.filterValues { it > 0 }

    /**
     * What is on the phone for the earlier clips' bookkeeping: the clip cache's files and the car's library's (a clip there
     * is linked into the cache when played), listed once.
     */
    fun onPhone(files: AudioFiles): (String) -> Boolean {
        val names = HashSet<String>()
        files.clips.list()?.let { names.addAll(it) }
        files.roadClips.list()?.let { names.addAll(it) }
        return { it in names }
    }

    /** A file of the clip cache [dir] played in the last [BUSY_MS] (marked used: its modification time). */
    fun busy(dir: File, now: Long = System.currentTimeMillis()): (String) -> Boolean = { name ->
        val f = File(dir, name)
        f.isFile && now - f.lastModified() < BUSY_MS
    }

    /** The app's index's files, when the app is open. */
    fun appFiles(): Set<String> = appIndex?.invoke()?.values?.flatMapTo(HashSet()) { v -> v.values.map(::fileOf) }.orEmpty()

    /**
     * The index of [files] changed from [olds] (newest first) to [new]: the earlier clips recorded ([swap]) and those done
     * with deleted. Returns the deleted files' sizes. Nothing for an empty [new] (a bad index must not forget them all).
     */
    fun swapIn(files: AudioFiles, olds: List<ClipIndex>, new: ClipIndex, referenced: Set<String> = appFiles()): Map<String, Long> {
        if (new.isEmpty()) return emptyMap()
        val has = onPhone(files)
        val busy = busy(files.clips)
        val gone = update(files.clips) { cur ->
            val s = swap(olds, new, cur, has, busy, referenced)
            s.previous to s.delete
        }
        return delete(files.clips, gone)
    }

    /**
     * A pass over the earlier clips of [files] against [index] ([settle]): those whose new files are here deleted. Returns
     * the deleted files' sizes. Nothing for an empty [index].
     */
    fun settleIn(files: AudioFiles, index: ClipIndex, referenced: Set<String> = appFiles(), only: Set<String>? = null): Map<String, Long> {
        if (index.isEmpty() || read(files.clips).isEmpty()) return emptyMap()
        val has = onPhone(files)
        val busy = busy(files.clips)
        val gone = update(files.clips) { cur ->
            val s = settle(index, cur, has, busy, referenced, only)
            s.previous to s.delete
        }
        return delete(files.clips, gone)
    }

    /**
     * One phrase's URL in [voice] changed from [old] to [new] in the app's index alone (the node voiced it now in another
     * voice than asked, newer than the index's): as [swapIn], for that entry only; [settleIn] lets it go once [new] is here.
     */
    fun replacedIn(files: AudioFiles, key: String, voice: String, old: String, new: String) {
        val has = onPhone(files)
        update(files.clips) { cur ->
            val (rest, had) = without(cur, key, voice)
            val earlier = supersede(listOf(mapOf(key to mapOf(voice to old))), mapOf(key to mapOf(voice to new)), had?.let { mapOf(key to mapOf(voice to it)) }.orEmpty(), has)
            val kept = earlier[key]?.get(voice) ?: return@update rest to Unit
            rest + (key to (rest[key].orEmpty() + (voice to kept))) to Unit
        }
    }

    /** The cap dropped [names] from the clip cache: their entries go. */
    fun droppedIn(dir: File, names: Set<String>) {
        if (names.isEmpty() || read(dir).isEmpty()) return
        update(dir) { cur -> without(cur, names) to Unit }
    }

    /**
     * [key] in [voice] was re-recorded on request: its earlier clip no longer stands in, and its file goes unless [index]
     * still plays it.
     */
    fun forgetIn(dir: File, key: String, voice: String, index: ClipIndex) {
        if (read(dir).isEmpty()) return
        val name = update(dir) { cur ->
            val (rest, url) = without(cur, key, voice)
            val name = url?.let(::fileOf)?.takeIf { n ->
                (index.values + rest.values).none { v -> v.values.any { fileOf(it) == n } }
            }
            rest to name
        } ?: return
        File(dir, name).delete()
    }
}
