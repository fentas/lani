package si.lanisce.lani.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Module
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.widget.VillageWidget

/** What there is to practise: the learner's dashboard (due cards, streak, word pool) and the tutor's modules. */
class ContentController(
    private val context: Context,
    private val prefs: Prefs,
    private val bridge: () -> Bridge?,
    /** The dashboard was just saved for the widget; it redraws. */
    private val onDashboard: () -> Unit,
) {
    var dashboard by mutableStateOf<Dashboard?>(null)
        private set
    /**
     * The review decks of the learner's other languages (practised on visits), by code: each its own GET /state?language=,
     * so their cards never mix into the home language's review, stats or the village's words.
     */
    var decks by mutableStateOf<Map<String, Dashboard>>(emptyMap())
        private set
    var modules by mutableStateOf<List<ModuleInfo>>(emptyList())
        private set
    /** Modules published (or updated) since Jan last opened them. */
    var newModules by mutableStateOf<Set<String>>(emptySet())
        private set
    /** A module list came from the node; tutor quests of modules not listed yet must not vanish before. */
    var modulesLoaded = false
        private set
    /** Full modules by id, for playing and for village challenges. */
    private val cache = mutableMapOf<String, Module>()

    /** Words learned (answered right at least once): "you know N words", and what the village's ages wait for. */
    val wordsKnown: Int get() = dashboard?.wordsLearned ?: 0

    /** Rusty words (their last review failed): the village's next age waits until a review polishes them. */
    val wordsRusty: Int get() = dashboard?.rusty?.size ?: 0

    /** The learner's target level (the last age of the village is reached with it). */
    val targetLevel: String get() = dashboard?.targetLevel ?: si.lanisce.lani.game.Catalog.DEFAULT_TARGET

    /**
     * Review answers ([results]: item id → quality) the node hasn't counted yet: a rusty word answered right is
     * polished at once, and one not learned yet learned, so the village's next age and a building's upgrade don't wait
     * for the node (offline, the outbox). The next reload brings the node's own count, new rust included.
     */
    fun reviewed(results: List<Pair<String, Int>>) {
        val d = dashboard ?: return
        val n = d.reviewed(results)
        if (n !== d) dashboard = n
    }

    /** The learner's words a dialog just reviewed ([ids]), before the node has them ([Dashboard.reviewedInDialog]). */
    fun reviewedInDialog(ids: Collection<String>, today: java.time.LocalDate = java.time.LocalDate.now()) {
        val d = dashboard ?: return
        val n = d.reviewedInDialog(ids, today)
        if (n !== d) dashboard = n
    }

    /**
     * Words of pack [packId] just learned in the app ([results]: word id → quality; [words]: the pack's), before the node
     * has them: the ones answered right count at once ([Dashboard.learnedWords]), as a pack's words do.
     */
    fun learnedWords(packId: String, words: List<si.lanisce.lani.data.PackWord>, results: List<Pair<String, Int>>) {
        val d = dashboard ?: return
        val n = d.learnedWords(packId, words, results)
        if (n !== d) dashboard = n
    }

    /** The village's treasure hunt (set by the view model): the level it brought counts before the node has it. */
    var treasure: () -> si.lanisce.lani.game.TreasureHunt? = { null }

    /**
     * The learner's level in [language] ("sl" → "A2"; A1 before the node said), as the app goes by it: the node's
     * ([Dashboard.levelIn]), or, in the village's own language, the level the treasure just brought while the node doesn't
     * have it yet ([si.lanisce.lani.game.Treasure.level]): the new rules, stories and readings don't wait for it.
     */
    fun levelIn(language: String): String {
        val node = dashboard?.levelIn(language) ?: "A1"
        return if (language == si.lanisce.lani.l10n.L10n.ownPair.target.code) si.lanisce.lani.game.Treasure.level(node, treasure()) else node
    }

    /** The review deck of [language]: the home language's dashboard for null, else that language's (when loaded). */
    fun deck(language: String?): Dashboard? = if (language == null) dashboard else decks[language]

    suspend fun reload(b: Bridge) {
        val d = b.dashboard()
        dashboard = d
        VillageWidget.saveStats(context, d.streak, d.dueCards.size)
        onDashboard()
        // Only a bridge that knows the language answers with it (an older one would send the home deck again).
        decks = buildMap {
            for (l in d.otherLanguages) {
                val deck = try {
                    b.dashboard(l.code)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null // that deck waits for the next reload; the home language's is here
                }
                if (deck?.language == l.code) put(l.code, deck)
            }
        }
        modules = b.modules().filter { "retired" !in it.tags }
        modulesLoaded = true
        val seen = prefs.seenModules()
        newModules = modules.filter { "${it.id}:${it.version}" !in seen }.map { it.id }.toSet()
    }

    /** Fetches module [id] to play it, and marks this version seen. */
    suspend fun open(id: String): Module? {
        val m = bridge()?.module(id) ?: return null
        prefs.markSeen("${m.id}:${m.version}")
        newModules = newModules - m.id
        cache[m.id] = m
        return m
    }

    /** Fetches the exercises of listed modules that aren't cached in their current version. */
    suspend fun loadModuleExercises() {
        val b = bridge() ?: return
        for (m in modules) {
            if (cache[m.id]?.version == m.version) continue
            runCatching { b.module(m.id) }.getOrNull()?.let { cache[m.id] = it }
        }
    }

    /** Every playable exercise of the cached modules, with its module id. */
    fun moduleExercises(): List<Pair<String, Exercise>> =
        cache.values.flatMap { m -> m.exercises.filter { it !is Exercise.Unsupported }.map { m.id to it } }
}
