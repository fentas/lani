package si.lanisce.lani.road

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Where getting the road ready is. */
sealed interface RoadProgress {
    /** Asking the node what there is to hear (the app open, a few seconds). */
    data object Gathering : RoadProgress

    /**
     * Getting the files onto the phone: [clips] of [clipsTotal] clip files and [prompts] of [promptsTotal] prompts done
     * (there, or found not to be had); [playable] once the first block is on the phone and the sessions can start.
     */
    data class Files(val clips: Int, val clipsTotal: Int, val prompts: Int, val promptsTotal: Int, val playable: Boolean = false) : RoadProgress {
        /** How far, by the time it takes: a prompt counts as [PROMPT_WEIGHT] clips. */
        val percent: Int get() {
            val total = clipsTotal + PROMPT_WEIGHT * promptsTotal
            return if (total == 0) 0 else ((clips + PROMPT_WEIGHT * prompts) * 100L / total).toInt()
        }

        companion object {
            /**
             * A prompt takes the phone's voice about ten times as long as a clip takes to download (the emulator, the dev
             * bridge: 2,612 prompts in about 140 s, 4,276 clips in about 10 s; a phone over the network downloads slower).
             */
            const val PROMPT_WEIGHT = 10
        }
    }
}

/**
 * Gets the files of a [RoadPlan] onto the phone ([RoadStore]) in [RoadWork.order], what plays first first: the clips
 * ([fetch]: into [RoadStore.clip], from the clip cache or the node, a few at once), the prompts ([render]: into
 * [RoadStore.prompt], the phone's voice, one by one) and the quiz's target-language texts the voice store lacked ([speak]:
 * into [RoadStore.spoken], an item's at once) go down the order side by side, and an item is ready once all passed it. When
 * the first block of "🚗 Za pot" is ready the library is written with what is ready ([publish]): the sessions can start;
 * then again every [PUBLISH_MS] while the rest comes in, and at the end with everything that could be had. What is on the
 * phone already is kept, so a run stopped halfway goes on where it stopped. No Android here: the tests run it with fakes.
 */
class RoadPrepWork(
    private val store: RoadStore,
    private val fetch: suspend (file: String) -> Boolean,
    private val render: suspend (text: String) -> Boolean,
    private val progress: (RoadProgress.Files) -> Unit = {},
    private val publish: (RoadLibrary) -> Unit = {},
    private val today: String = LocalDate.now().toString(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** The texts' files had, each true or false in their order (none by default: a quiz question needing one isn't played). */
    private val speak: suspend (List<Sound.Spoken>) -> List<Boolean> = { l -> l.map { false } },
) {
    /**
     * How a run ended: the library written, the clips, prompts and spoken texts that couldn't be had, whether the phone had
     * no voice.
     */
    data class Result(
        val library: RoadLibrary,
        val clipsTried: Int,
        val clipsFailed: Int,
        val promptsFailed: Int,
        val noVoice: Boolean = false,
        val spokenFailed: Int = 0,
    )

    /** [render] throws it when the phone has no voice for the prompts: the run ends with what plays without them. */
    class NoVoice : Exception("no voice for the prompts")

    /** One thing at a time here (the counters below); the files themselves on the IO threads. */
    private val one = Dispatchers.Default.limitedParallelism(1)

    suspend fun run(plan: RoadPlan): Result = withContext(one) {
        val lib = plan.library
        val order = RoadWork.order(lib, withContext(Dispatchers.IO) { store.readHeard() }, today)
        val items = order.items
        val clipsOf = items.map(RoadWork::clips)
        val promptsOf = items.map(RoadWork::prompts)
        val spokenOf = items.map(RoadWork::spoken)
        val allClips = clipsOf.flatten().toSet()
        val allPrompts = promptsOf.flatten().toSet()
        val allSpoken = spokenOf.flatten().toSet()
        val gotClips = HashSet<String>()
        val gotPrompts = HashSet<String>()
        val gotSpoken = HashSet<Sound.Spoken>()
        withContext(Dispatchers.IO) {
            store.clips.mkdirs()
            store.prompts.mkdirs()
            store.spoken.mkdirs()
            allClips.filterTo(gotClips) { store.clip(it).isFile }
            allPrompts.filterTo(gotPrompts) { store.prompt(lib.base, it).isFile }
            allSpoken.filterTo(gotSpoken) { store.spoken(it.voice, it.text).isFile }
        }
        var clipsDone = gotClips.size
        var promptsDone = gotPrompts.size
        var spokenDone = gotSpoken.size
        var clipsTried = 0
        var clipsFailed = 0
        var promptsFailed = 0
        var spokenFailed = 0
        // how far down the order each has come: the items before both are ready (or can't be had)
        var clipsAt = 0
        var promptsAt = 0
        var published = -1
        var spokenPublished = gotSpoken.size
        var library = lib.copy(items = emptyList())
        var publishing = false
        var lastPublish = 0L

        fun ready(item: RoadItem) = RoadWork.clips(item).all { it in gotClips } && RoadWork.prompts(item).all { it in gotPrompts } &&
            RoadWork.spoken(item).all { it in gotSpoken }

        // the spoken texts are rendered like the prompts: they count among them
        fun report() = progress(
            RoadProgress.Files(clipsDone, allClips.size, promptsDone + spokenDone, allPrompts.size + allSpoken.size, playable = published >= order.first),
        )

        suspend fun publishNow(at: Int) {
            publishing = true
            try {
                // the quiz's fixed phrases always: each is played from the first of its alternatives on the phone
                val now = lib.copy(items = lib.items.filter { it.kind == Kind.KIT || ready(it) })
                withContext(Dispatchers.IO) { store.writeLibrary(now) }
                library = now
                published = at
                spokenPublished = gotSpoken.size
                lastPublish = clock()
                publish(now)
                report()
            } finally {
                publishing = false
            }
        }

        suspend fun maybePublish() {
            val at = minOf(clipsAt, promptsAt)
            if (publishing || at < order.first) return
            // further down the order, or quiz questions whose texts were voiced since
            if (at <= published && gotSpoken.size <= spokenPublished) return
            if (published >= order.first && clock() - lastPublish < PUBLISH_MS) return
            publishNow(at)
        }

        var noVoice = false
        try {
            coroutineScope {
                launch {
                    // each clip once, where it is first needed; a few at once
                    val queue = items.indices.flatMap { i -> clipsOf[i].map { i to it } }.filter { (_, f) -> f !in gotClips }.distinctBy { it.second }
                    var next = 0
                    for (batch in queue.chunked(CLIPS_AT_ONCE)) {
                        val ok = batch.map { (_, f) ->
                            async {
                                try {
                                    fetch(f)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    false
                                }
                            }
                        }.awaitAll()
                        batch.zip(ok).forEach { (p, good) -> if (good) gotClips += p.second else clipsFailed++ }
                        clipsTried += batch.size
                        clipsDone += batch.size
                        next += batch.size
                        clipsAt = if (next < queue.size) queue[next].first else items.size
                        report()
                        maybePublish()
                    }
                    clipsAt = items.size
                    maybePublish()
                }
                launch {
                    val tried = HashSet(gotPrompts)
                    for ((i, texts) in promptsOf.withIndex()) {
                        for (t in texts) {
                            if (!tried.add(t)) continue
                            if (render(t)) gotPrompts += t else promptsFailed++
                            promptsDone++
                            report()
                        }
                        promptsAt = i + 1
                        maybePublish()
                    }
                }
                launch {
                    // an item's texts at once (the node voices a few in one ask); a question waits for them, the rest doesn't
                    val tried = HashSet(gotSpoken)
                    for (texts in spokenOf) {
                        val todo = texts.filter { tried.add(it) }
                        if (todo.isEmpty()) continue
                        val ok = try {
                            speak(todo)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            todo.map { false }
                        }
                        todo.forEachIndexed { k, t -> if (ok.getOrElse(k) { false }) gotSpoken += t else spokenFailed++ }
                        spokenDone += todo.size
                        report()
                        maybePublish()
                    }
                }
            }
        } catch (e: NoVoice) {
            noVoice = true
        }
        publishNow(items.size)
        Result(library, clipsTried, clipsFailed, promptsFailed, noVoice, spokenFailed)
    }

    companion object {
        /** While the rest comes in, the library is written again at most this often. */
        const val PUBLISH_MS = 15_000L

        const val CLIPS_AT_ONCE = 4
    }
}
