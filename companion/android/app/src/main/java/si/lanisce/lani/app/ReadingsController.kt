package si.lanisce.lani.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.json
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ReadAloud
import si.lanisce.lani.data.ReadingPractice
import si.lanisce.lani.data.WordExample
import si.lanisce.lani.data.Words
import si.lanisce.lani.data.Writes
import si.lanisce.lani.game.Readings
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.ReadingFile
import si.lanisce.lani.game.scene.StoryBook
import si.lanisce.lani.game.scene.StoryTonight
import si.lanisce.lani.ui.game.ReadWord
import java.io.File

/**
 * The reading corner ("📖 Branje · Reading", companion/GAME.md "The reading corner"): its readings (the culture pack's,
 * bundled, and the bridge's with the tutor's, cached for offline), and the reading practice the app reports ([ReadingPractice]):
 * a reading's questions, a story heard to its end, a notebook entry's "🎯 Preveri se" answered, a reading aloud. Every report goes to the node through the outbox
 * (POST /readings/done); a node from before readings gets it as a session_end for the tutor to persist.
 */
class ReadingsController(
    context: Context,
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val sync: SyncController,
    private val game: GameController,
    private val notices: Notices,
    /** The `from` a word added here is sent with ([WordsController.sourceFor]): "reading" to a bridge that takes it. */
    private val source: suspend (String) -> String = { Words.VILLAGER },
) {
    private val cache = File(context.filesDir, "readings.json")
    private val io = Dispatchers.IO.limitedParallelism(1)
    private var served: List<ReadingFile>? = null

    /** Whether the node has the reading corner (GET /readings); null until asked. */
    private var nodeHasReadings: Boolean? = null

    /** The readings of the learner's own village: the culture pack's, then the tutor's. */
    var all by mutableStateOf<List<ReadingFile>>(emptyList())
        private set

    /** Words added from a reading's list ("reading/word" keys), shown ✓. */
    var added by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        scope.launch {
            served = withContext(io) { runCatching { ReadingPractice.parseServed(cache.readText()) }.getOrNull() }
            merge()
        }
    }

    /** The culture pack the corner is of: the learner's own village's (not a visited town's). */
    private val culture: String get() = Cultures.home.id

    private fun merge() {
        val c = culture
        val bundled = Cultures.library(c)
        // the node's copy of a curated reading is the newer; a reading the app can't read is left out
        val fromNode = served.orEmpty().filter { (it.source != "curated" || it.culture == null || it.culture == c) && Cultures.check(c, it).isEmpty() }
        val ids = fromNode.map { it.id }.toSet()
        all = bundled.filter { it.id !in ids } + fromNode
    }

    /** The village is in another culture pack now: its readings. */
    fun onCulture() = merge()

    /** The bridge's readings; an older bridge (404) or none now keeps what the app has. */
    suspend fun reload(b: Bridge) {
        val got = try {
            b.readings()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // offline
        }
        nodeHasReadings = got != null
        if (got == null) return
        served = got
        withContext(io) { runCatching { cache.writeText(json.encodeToString(ListSerializer(ReadingFile.serializer()), got)) } }
        merge()
    }

    fun byId(id: String): ReadingFile? = all.firstOrNull { it.id == id } ?: Cultures.home.readings[id]

    /** The readings read (their questions answered), by id. */
    fun read(): Set<String> {
        val prefix = "$culture/"
        return game.state?.read.orEmpty().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    }

    // --- reading practice -----------------------------------------------------------------------------------------

    private fun report(body: JsonObject) {
        if (nodeHasReadings == false) {
            // a node from before readings: the tutor persists it, as a module's run
            val (summary, data) = ReadingPractice.sessionEnd(body, ReadingPractice.summary(body))
            sync.submitLater(Writes.message("session_end", "main", summary, data))
        } else {
            sync.submitLater(Writes.readingDone(body))
        }
    }

    /**
     * Reading [r]'s questions were answered ([verdicts], the first picks) after [minutes] on screen, its text brought back
     * [looks] times while a question was open: the village pays the first time ([Readings.finish]), less [cost] percent for
     * the looks ([si.lanisce.lani.game.Peeks]), and every time it counts as reading practice. Returns what it paid.
     */
    fun answered(r: ReadingFile, verdicts: List<Verdict>, minutes: Int, looks: Int = 0, cost: Int = 0): Map<Res, Int> {
        val paid = game.finishReading(r.id, verdicts, cost)
        if (verdicts.isNotEmpty()) report(ReadingPractice.reading(r, r.title.target, verdicts, minutes, looks))
        return paid
    }

    /** Reading [r] (or a part of it) was read aloud: [takes], the last of each part; [minutes] on screen. */
    fun readAloud(r: ReadingFile, takes: List<ReadAloud.Take>, minutes: Int) {
        if (takes.isEmpty()) return
        report(ReadingPractice.aloud(r, r.title.target, takes, minutes))
    }

    /** Tonight's story [t] was heard to its end: [turns] of the learner's, [missed] not right the first time, [minutes] on screen. */
    fun storyHeard(t: StoryTonight, turns: Int, missed: Int, minutes: Int) {
        report(ReadingPractice.story(t.story.id, t.title, t.level, turns, missed, minutes))
    }

    // --- the story notebook's questions ("🎯 Preveri se", ui/notebook) ---------------------------------------------

    /** The stories heard whose questions were answered to the last, as "zlatorog/A1". */
    fun booksRead(): Set<String> {
        val prefix = "$culture/${ReadingPractice.BOOK}"
        return game.state?.read.orEmpty().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    }

    /** Book [id] was read to its questions' end at [level] before. */
    fun bookDone(id: String, level: String): Boolean = "$id/$level" in booksRead()

    /**
     * Book [b]'s questions were answered at [level] ([verdicts], the first picks) after [minutes] on screen, its text
     * brought back [looks] times while a question was open: it is read at that level, and every time it counts as reading
     * practice.
     */
    fun bookRead(b: StoryBook, level: String, verdicts: List<Verdict>, minutes: Int, looks: Int = 0) {
        game.bookRead("$culture/${ReadingPractice.BOOK}${b.id}/$level")
        if (verdicts.isNotEmpty()) report(ReadingPractice.book(b.id, b.story.title.substringBefore(" · "), level, verdicts, minutes, looks))
    }

    /** Adds new word [w] of reading [r] to the learner's words (a review item), once. */
    fun addWord(r: ReadingFile, w: ReadWord) {
        val key = "${r.id}/${w.word}"
        if (key in added) return
        added = added + key
        val example = w.line.takeIf { it.isNotBlank() }?.let { WordExample(it, w.lineBase) }
        scope.launchSafely(notices::fail) {
            sync.submit(Writes.addWord(sl = w.word, en = w.means, pos = w.pos, example = example, from = source(Words.READING)))
        }
    }
}
