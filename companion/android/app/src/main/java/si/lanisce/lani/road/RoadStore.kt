package si.lanisce.lani.road

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import si.lanisce.lani.data.Outbox
import java.io.File

/**
 * The road's files on the phone (the app's filesDir/road, not the cache: the clip cache's LRU never drops them):
 * road.json (the [RoadLibrary]: what plays), plan.json (what getting ready still has to do, [RoadPlan]), clips/ (the
 * Slovene clips), prompts/ (the base-language prompts), heard.json (when each item was last heard) and ratings.json
 * (ratings not handed to the outbox yet).
 */
class RoadStore(val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "road"))

    val clips = File(dir, "clips")
    val prompts = File(dir, "prompts")
    private val library = File(dir, "road.json")
    private val plan = File(dir, "plan.json")
    private val heard = File(dir, "heard.json")
    private val ratings = File(dir, "ratings.json")

    fun readLibrary(): RoadLibrary? = read(library)?.let { runCatching { RoadPlay.json.decodeFromString(RoadLibrary.serializer(), it) }.getOrNull() }

    fun writeLibrary(lib: RoadLibrary) = write(library, RoadPlay.json.encodeToString(RoadLibrary.serializer(), lib))

    /** What getting ready still has to do, or null when nothing is under way. */
    fun readPlan(): RoadPlan? = read(plan)?.let { runCatching { RoadPlay.json.decodeFromString(RoadPlan.serializer(), it) }.getOrNull() }

    fun writePlan(p: RoadPlan) = write(plan, RoadPlay.json.encodeToString(RoadPlan.serializer(), p))

    /** Getting ready is done (or given up): nothing to go on with. */
    fun clearPlan() = synchronized(LOCK) { plan.delete() }

    val planned: Boolean get() = plan.isFile

    fun clip(file: String) = File(clips, file)

    fun prompt(base: String, text: String) = File(prompts, RoadPlay.promptFile(base, text))

    /** Every file [item] plays is on the phone. */
    fun playable(item: RoadItem, base: String): Boolean = item.sounds.all { s ->
        when (s) {
            is Sound.Clip -> s.files.all { clip(it).isFile }
            is Sound.Prompt -> prompt(base, s.text).isFile
            is Sound.Pause -> true
        }
    }

    fun readHeard(): Map<String, Long> = read(heard)?.let { runCatching { RoadPlay.json.decodeFromString(HEARD, it) }.getOrNull() }.orEmpty()

    /** [id] (an item's, a word heard again counts as the word) heard now. */
    fun heard(id: String, at: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        write(heard, RoadPlay.json.encodeToString(HEARD, readHeard() + (id.substringBefore('#') to at)))
    }

    fun readRatings(): List<Rating> = read(ratings)?.let { runCatching { RoadPlay.json.decodeFromString(RATINGS, it) }.getOrNull() }.orEmpty()

    fun rate(r: Rating) = synchronized(LOCK) { write(ratings, RoadPlay.json.encodeToString(RATINGS, RoadRatings.add(readRatings(), r))) }

    /**
     * Hands the ratings waiting here to the app's outbox ([outbox]; the app or the service sends it when the node can be
     * reached): one review write for the cards, one learn write per pack. Returns how many writes were queued.
     */
    fun commit(outbox: Outbox): Int = synchronized(LOCK) {
        val all = readRatings()
        if (all.isEmpty()) return 0
        val writes = RoadRatings.writes(all)
        writes.forEach(outbox::enqueue)
        ratings.delete()
        writes.size
    }

    private fun read(f: File): String? = synchronized(LOCK) { runCatching { f.readText() }.getOrNull() }

    private fun write(f: File, raw: String) = synchronized(LOCK) {
        dir.mkdirs()
        val tmp = File(dir, "${f.name}.tmp")
        tmp.writeText(raw)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    private companion object {
        val LOCK = Any()
        val HEARD = MapSerializer(String.serializer(), Long.serializer())
        val RATINGS = ListSerializer(Rating.serializer())
    }
}
