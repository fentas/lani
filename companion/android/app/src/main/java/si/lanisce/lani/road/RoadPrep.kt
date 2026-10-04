package si.lanisce.lani.road

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.l10n.bi
import java.io.File

/**
 * Getting the road ready as the sheet and the notification show it, for the whole app: [RoadPrepService] gets the files,
 * [RoadPrep] (the sheet's) reads it. Compose state, written on the main thread.
 */
object RoadPrepState {
    var progress by mutableStateOf<RoadProgress?>(null)

    /** Why the last getting ready failed or fell short, to show. */
    var problem by mutableStateOf<String?>(null)

    /** The library on the phone as last written (null until read, or when there is none): it grows while getting ready. */
    var library by mutableStateOf<RoadLibrary?>(null)

    /** The service is getting the files. */
    var working by mutableStateOf(false)

    /** The last getting ready got everything it could: the sheet says it's done. */
    var done by mutableStateOf(false)
}

/**
 * "🚗 Pripravi za pot · Get ready for the road": everything the car plays, onto the phone ([RoadStore]), so the drive works
 * without the node. The app gathers the items ([RoadGather]: a few seconds, the node reachable) into a plan ([RoadPlan]);
 * [RoadPrepService] then gets their Slovene clips (from the phone's clip cache, else downloaded from the voice store, never
 * voiced now) and renders the base-language prompts ([RoadPrompts]) in the background, what plays first first, and writes
 * the library as it grows: the sessions can start once the first block is there. Items whose files couldn't be had are
 * left out. Getting ready again takes only what's new; a run stopped halfway goes on when the app opens.
 */
class RoadPrep(private val context: Context, private val scope: CoroutineScope) {
    val store = RoadStore(context)

    val library: RoadLibrary? get() = RoadPrepState.library
    val progress: RoadProgress? get() = RoadPrepState.progress
    val problem: String? get() = RoadPrepState.problem

    /** Everything that could be had is on the phone (this time the app is open). */
    val done: Boolean get() = RoadPrepState.done

    private var gathering by mutableStateOf(false)
    val running: Boolean get() = gathering || RoadPrepState.working

    init {
        scope.launch {
            val (lib, planned) = withContext(Dispatchers.IO) { store.readLibrary() to store.planned }
            if (RoadPrepState.library == null) RoadPrepState.library = lib
            // a run stopped halfway (the app or the phone stopped): on where it stopped
            if (planned && !RoadPrepState.working) RoadPrepService.start(context)
        }
    }

    /**
     * Gets the road ready: [gather] what there is (it throws when the node can't be reached), then the files in the
     * background. [cache] is the phone's clip cache ([si.lanisce.lani.data.Clips.dir]), copied from first. [youSay] and
     * [drillWords] are the prompts in the learner's base language. [mini]: QA's small library ([RoadWork.mini]).
     */
    fun start(
        gather: suspend () -> RoadInputs,
        youSay: (String) -> String,
        cache: File,
        drillWords: RoadDrills.Words = RoadDrills.Words.EN,
        mini: Boolean = false,
    ) {
        if (running) return
        RoadPrepState.problem = null
        RoadPrepState.done = false
        gathering = true
        RoadPrepState.progress = RoadProgress.Gathering
        scope.launch {
            try {
                val inputs = gather()
                val urls = HashMap<String, String>()
                val lookup = ClipLookup.of(inputs.index).let { l ->
                    ClipLookup { t, v -> l.urls(t, v)?.also { us -> us.forEach { urls[RoadPlay.fileOf(it)] = it } } }
                }
                val plan = withContext(Dispatchers.Default) {
                    val built = RoadGather.library(inputs, youSay, clips = lookup, drillWords = drillWords).let { if (mini) RoadWork.mini(it) else it }
                    val files = built.items.flatMap(RoadWork::clips).toSet()
                    RoadPlan(built, urls.filterKeys { it in files }, cache.path)
                }
                withContext(Dispatchers.IO) { store.writePlan(plan) }
                RoadPrepState.working = true
                if (!RoadPrepService.start(context)) {
                    RoadPrepState.working = false
                    RoadPrepState.progress = null
                }
            } catch (e: CancellationException) {
                RoadPrepState.progress = null
                throw e
            } catch (e: Exception) {
                RoadPrepState.progress = null
                RoadPrepState.problem = bi("road.failed", "why" to (e.message ?: e.javaClass.simpleName))
            } finally {
                gathering = false
            }
        }
    }

    /** "5 h 20 min" of listening on the phone, or null when nothing is ready. */
    val time: String? get() = library?.takeIf { it.items.isNotEmpty() }?.let { RoadGather.duration(it.seconds) }
}
